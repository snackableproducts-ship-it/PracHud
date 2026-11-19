package com.practicehud;

import com.practicehud.Hud.Row;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Combat utilities that sit on top of the HUD: input statistics (CPS, W-tap), totem pops, combat
 * tag, blast-damage maths, projectile prediction, and the screen-space overlays that go with them.
 * Everything here is client-side and read-only except {@link #middleClick}, which is a restricted
 * module held off by Competitive Safe Mode.
 */
public final class Combat {
    private Combat() {}

    // =====================================================================
    // Input statistics (sampled every rendered frame)
    // =====================================================================

    private static final ArrayDeque<Long> LMB_CLICKS = new ArrayDeque<>();
    private static final ArrayDeque<Long> RMB_CLICKS = new ArrayDeque<>();
    private static boolean lmbDown, rmbDown;
    private static int peakCps;
    private static boolean fwdDown, backDown;
    private static long fwdReleasedAt, backReleasedAt;
    private static long tapGapMs = -1;
    private static String tapKey = "W";
    private static long tapAt;

    public static void sampleInput(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        prune(LMB_CLICKS, now);
        prune(RMB_CLICKS, now);

        if (mc.currentScreen == null) {
            long window = mc.getWindow().getHandle();
            boolean l = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean r = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            if (l && !lmbDown) {
                LMB_CLICKS.addLast(now);
                onAttackClick(mc, now);
            }
            if (r && !rmbDown) RMB_CLICKS.addLast(now);
            lmbDown = l;
            rmbDown = r;
        } else {
            lmbDown = false;
            rmbDown = false;
        }
        peakCps = Math.max(peakCps, Math.max(LMB_CLICKS.size(), RMB_CLICKS.size()));

        // W-tap / S-tap: gap between releasing a movement key and pressing it again.
        boolean f = mc.options.forwardKey.isPressed();
        boolean b = mc.options.backKey.isPressed();
        if (f && !fwdDown && now - fwdReleasedAt < 400) { tapGapMs = now - fwdReleasedAt; tapKey = "W"; tapAt = now; }
        if (!f && fwdDown) fwdReleasedAt = now;
        if (b && !backDown && now - backReleasedAt < 400) { tapGapMs = now - backReleasedAt; tapKey = "S"; tapAt = now; }
        if (!b && backDown) backReleasedAt = now;
        fwdDown = f;
        backDown = b;
    }

    private static void prune(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.pollFirst();
    }

    public static int lmbCps() { return LMB_CLICKS.size(); }
    public static int rmbCps() { return RMB_CLICKS.size(); }
    public static int peakCps() { return peakCps; }

    /** Last W/S-tap gap in ms, or -1 when there has been no recent tap. */
    public static long recentTapGapMs() { return System.currentTimeMillis() - tapAt < 3000 ? tapGapMs : -1; }
    public static String tapKey() { return tapKey; }

    public static int ping(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null || mc.player == null) return -1;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? -1 : entry.getLatency();
    }

    // ---- Server TPS estimate (from the interval between the server's time-update packets) ----

    private static long lastTimeUpdate;
    private static double tps = 20.0;
    private static long tpsAt;

    /** Called from the network mixin each time the server sends a world-time update (about once a second). */
    public static void onTimeUpdate() {
        long now = System.nanoTime();
        if (lastTimeUpdate != 0L) {
            double seconds = (now - lastTimeUpdate) / 1_000_000_000.0;
            if (seconds > 0.25 && seconds < 5.0) {
                double sample = Math.min(20.0, 20.0 / seconds);
                tps = tps * 0.6 + sample * 0.4;
                tpsAt = System.currentTimeMillis();
            }
        }
        lastTimeUpdate = now;
    }

    /** Estimated server TPS, or -1 when no recent data (e.g. singleplayer, or the hook is unavailable). */
    public static double tps() { return System.currentTimeMillis() - tpsAt < 6000 ? tps : -1.0; }

    // =====================================================================
    // Totem pops
    // =====================================================================

    private static final class Pop {
        String name = "?";
        int count;
        long last;
    }

    private static final Map<UUID, Pop> POPS = new HashMap<>();

    /** Called from the network mixin on entity status 35 (totem of undying used). */
    public static void onTotemPop(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Pop pop = POPS.computeIfAbsent(entity.getUuid(), k -> new Pop());
        pop.name = entity.getName().getString();
        pop.count++;
        pop.last = System.currentTimeMillis();
        if (Config.get().totemChatAlert && Modules.enabled("totem_pops") && mc.player != null && entity != mc.player) {
            mc.player.sendMessage(Text.literal("§6[XMCore] §f" + pop.name + " popped a totem §7(#" + pop.count + ")"), false);
        }
    }

    public static int pops(UUID id) {
        Pop pop = POPS.get(id);
        return pop == null ? 0 : pop.count;
    }

    public static void renderPops(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        long now = System.currentTimeMillis();
        List<Pop> recent = new ArrayList<>();
        for (Pop pop : POPS.values()) if (now - pop.last < 120_000L) recent.add(pop);
        recent.sort(Comparator.comparingLong((Pop x) -> x.last).reversed());
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(5, recent.size()); i++) {
            Pop pop = recent.get(i);
            rows.add(Row.of(pop.name + ": " + pop.count + (pop.count == 1 ? " pop" : " pops"), pop.count >= 2 ? Hud.RED : Hud.GOLD));
        }
        if (rows.isEmpty()) rows.add(Row.of("No totem pops yet", Hud.GRAY));
        Hud.panel(ctx, "Totem Pops", Hud.GOLD, rows.toArray(new Row[0]));
    }

    // =====================================================================
    // Combat tag
    // =====================================================================

    private static final long TAG_MS = 15_000L;
    private static long tagUntil;
    private static int lastHurt;
    private static long stunUntil;

    private static void onAttackClick(MinecraftClient mc, long now) {
        if (mc.player == null) return;
        Entity target = mc.targetedEntity;
        if (target instanceof PlayerEntity victim && victim != mc.player) {
            tagUntil = now + TAG_MS;
            // An axe hit on a raised shield disables it for five seconds.
            if (victim.isBlocking() && mc.player.getMainHandStack().isIn(ItemTags.AXES)) stunUntil = now + 5000L;
        }
    }

    public static void renderTag(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        long left = tagUntil - System.currentTimeMillis();
        if (left <= 0L) return;
        float frac = left / (float) TAG_MS;
        Hud.panel(ctx, "Combat Tag", Hud.RED,
                Row.bar(String.format("In combat: %.1fs", left / 1000.0), frac, frac > 0.33f ? Hud.RED : Hud.YELLOW),
                Row.of("Do not log out", Hud.GRAY));
    }

    // =====================================================================
    // Per-tick work
    // =====================================================================

    private static int blastTick;
    private static boolean sprintForced, sneakForced, prevSprint, prevSneak;

    public static void tick(MinecraftClient mc) {
        tickToggles(mc);
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            blasts = List.of();
            return;
        }

        // Being hit by a nearby player starts the combat tag.
        int hurt = p.hurtTime;
        if (hurt > 0 && lastHurt == 0) {
            for (PlayerEntity other : mc.world.getPlayers()) {
                if (other != p && other.squaredDistanceTo(p) < 12.0 * 12.0) {
                    tagUntil = System.currentTimeMillis() + TAG_MS;
                    break;
                }
            }
        }
        lastHurt = hurt;

        if (Modules.enabled("blast_calc")) {
            if ((++blastTick & 1) == 0) computeBlasts(mc);
        } else {
            blasts = List.of();
        }

        if (!POPS.isEmpty() && (p.age % 200) == 0) {
            long now = System.currentTimeMillis();
            POPS.values().removeIf(pop -> now - pop.last > 300_000L);
        }
    }

    /** Uses the vanilla "toggle" accessibility options so the state persists like a normal game setting. */
    private static void tickToggles(MinecraftClient mc) {
        if (mc.options == null) return;
        if (Modules.enabled("toggle_sprint")) {
            if (!sprintForced) { prevSprint = mc.options.getSprintToggled().getValue(); sprintForced = true; }
            if (!mc.options.getSprintToggled().getValue()) mc.options.getSprintToggled().setValue(true);
        } else if (sprintForced) {
            mc.options.getSprintToggled().setValue(prevSprint);
            sprintForced = false;
        }
        if (Modules.enabled("toggle_sneak")) {
            if (!sneakForced) { prevSneak = mc.options.getSneakToggled().getValue(); sneakForced = true; }
            if (!mc.options.getSneakToggled().getValue()) mc.options.getSneakToggled().setValue(true);
        } else if (sneakForced) {
            mc.options.getSneakToggled().setValue(prevSneak);
            sneakForced = false;
        }
    }

    public static void renderToggleStatus(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        List<Row> rows = new ArrayList<>();
        if (Modules.enabled("toggle_sprint")) rows.add(Row.of("Sprint: " + (p.isSprinting() ? "ON" : "toggle ready"), p.isSprinting() ? Hud.GREEN : Hud.GRAY));
        if (Modules.enabled("toggle_sneak")) rows.add(Row.of("Sneak: " + (p.isSneaking() ? "ON" : "toggle ready"), p.isSneaking() ? Hud.YELLOW : Hud.GRAY));
        if (rows.isEmpty()) return;
        Hud.panel(ctx, "Movement Mode", Hud.AQUA, rows.toArray(new Row[0]));
    }

    // =====================================================================
    // Middle-click utilities (restricted)
    // =====================================================================

    /** Pearl on middle-click, XP bottle while sneaking, firework while gliding. Uses normal item-use. */
    public static void middleClick(MinecraftClient mc) {
        if (!Modules.enabled("middle_click") || mc.player == null || mc.currentScreen != null
                || mc.interactionManager == null) return;
        ClientPlayerEntity p = mc.player;
        Config c = Config.get();
        Item want = null;
        if (p.isGliding() && c.middleFirework) want = Items.FIREWORK_ROCKET;
        else if (p.isSneaking() && c.middleXp) want = Items.EXPERIENCE_BOTTLE;
        else if (c.middlePearl) want = Items.ENDER_PEARL;
        if (want == null) return;

        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(want)) { slot = i; break; }
        }
        if (slot < 0) return;
        int previous = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        p.swingHand(Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(previous);
    }

    // =====================================================================
    // Hotbar re-gear helper
    // =====================================================================

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static String itemName(String id) {
        try {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) return id;
            return Registries.ITEM.get(identifier).getName().getString();
        } catch (Exception e) {
            return id;
        }
    }

    public static void saveHotbar(MinecraftClient mc) {
        if (mc.player == null) return;
        String[] preset = Config.get().hotbarPreset;
        for (int i = 0; i < 9; i++) preset[i] = itemId(mc.player.getInventory().getStack(i));
        Config.save();
    }

    public static void clearHotbar() {
        java.util.Arrays.fill(Config.get().hotbarPreset, "");
        Config.save();
    }

    public static void renderRegear(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        String[] preset = Config.get().hotbarPreset;
        boolean any = false;
        List<Row> rows = new ArrayList<>();
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        for (int i = 0; i < 9; i++) {
            String want = preset[i];
            if (want == null || want.isEmpty()) continue;
            any = true;
            if (want.equals(itemId(p.getInventory().getStack(i)))) continue;
            int x = sw / 2 - 91 + i * 20 + 1, y = sh - 22 + 1;
            ctx.fill(x, y, x + 20, y + 1, 0xFFFF4A5E);
            ctx.fill(x, y + 19, x + 20, y + 20, 0xFFFF4A5E);
            ctx.fill(x, y, x + 1, y + 20, 0xFFFF4A5E);
            ctx.fill(x + 19, y, x + 20, y + 20, 0xFFFF4A5E);
            if (rows.size() < 5) rows.add(Row.of("Slot " + (i + 1) + ": " + itemName(want), Hud.RED));
        }
        if (!any) {
            Hud.panel(ctx, "Hotbar Layout", Hud.GRAY, Row.of("No layout saved", Hud.GRAY), Row.of("Save one in Settings", Hud.GRAY));
        } else if (!rows.isEmpty()) {
            Hud.panel(ctx, "Hotbar Layout", Hud.RED, rows.toArray(new Row[0]));
        }
    }

    // =====================================================================
    // Blast damage (end crystals and respawn anchors)
    // =====================================================================

    private record Blast(String label, double distance, float damage, boolean lethal, Vec3d pos) {}

    private static List<Blast> blasts = List.of();
    private static final List<BlockPos> ANCHORS = new ArrayList<>();
    private static int anchorScan;

    private static void computeBlasts(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        float hp = p.getHealth() + p.getAbsorptionAmount();
        List<Blast> out = new ArrayList<>();

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                p.getBoundingBox().expand(13.0), e -> true)) {
            Vec3d src = crystal.getPos();
            float damage = blastDamage(mc, p, src, 6.0f);
            out.add(new Blast("Crystal", Math.sqrt(p.squaredDistanceTo(src)), damage, damage >= hp, src));
        }

        // Anchors only explode outside the Nether.
        if (!"the_nether".equals(mc.world.getRegistryKey().getValue().getPath())) {
            if ((anchorScan++ % 5) == 0) {
                ANCHORS.clear();
                BlockPos origin = p.getBlockPos();
                for (BlockPos pos : BlockPos.iterate(origin.add(-10, -10, -10), origin.add(10, 10, 10))) {
                    var state = mc.world.getBlockState(pos);
                    if (state.isOf(Blocks.RESPAWN_ANCHOR) && state.get(RespawnAnchorBlock.CHARGES) > 0) {
                        ANCHORS.add(pos.toImmutable());
                    }
                }
            }
            for (BlockPos pos : ANCHORS) {
                Vec3d src = Vec3d.ofCenter(pos);
                float damage = blastDamage(mc, p, src, 5.0f);
                out.add(new Blast("Anchor", Math.sqrt(p.squaredDistanceTo(src)), damage, damage >= hp, src));
            }
        } else {
            ANCHORS.clear();
        }

        out.sort(Comparator.comparingDouble((Blast b) -> b.damage()).reversed());
        blasts = out.size() > 5 ? new ArrayList<>(out.subList(0, 5)) : out;
    }

    /** Vanilla explosion damage to {@code victim} for an explosion of the given power at {@code src}. */
    private static float blastDamage(MinecraftClient mc, LivingEntity victim, Vec3d src, float power) {
        double q = power * 2.0;
        double dist = Math.sqrt(victim.squaredDistanceTo(src)) / q;
        if (dist > 1.0) return 0f;
        double impact = (1.0 - dist) * exposure(mc, victim, src);
        float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * q + 1.0);

        Difficulty difficulty = mc.world.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL) return 0f;
        if (difficulty == Difficulty.EASY) damage = Math.min(damage / 2f + 1f, damage);
        else if (difficulty == Difficulty.HARD) damage = damage * 3f / 2f;

        damage = afterArmor(victim, damage);
        if (victim.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amplifier = victim.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier();
            damage *= Math.max(0f, 1f - 0.2f * (amplifier + 1));
        }
        return Math.max(0f, damage);
    }

    /** Armour and toughness reduction (enchantments are not included). */
    private static float afterArmor(LivingEntity victim, float damage) {
        float armor = victim.getArmor();
        float toughness = (float) victim.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float f = 2f + toughness / 4f;
        float g = Math.max(armor * 0.2f, Math.min(20f, armor - damage / f));
        return damage * (1f - g / 25f);
    }

    /** Fraction of sample points on the victim that have a clear line to the explosion. */
    private static float exposure(MinecraftClient mc, LivingEntity victim, Vec3d src) {
        Box b = victim.getBoundingBox();
        Vec3d c = b.getCenter();
        double dx = (b.maxX - b.minX) * 0.4, dz = (b.maxZ - b.minZ) * 0.4;
        double[][] points = {
                {c.x, c.y, c.z}, {c.x, b.minY + 0.1, c.z}, {c.x, b.maxY - 0.1, c.z},
                {c.x - dx, c.y, c.z - dz}, {c.x + dx, c.y, c.z - dz},
                {c.x - dx, c.y, c.z + dz}, {c.x + dx, c.y, c.z + dz}
        };
        int clear = 0;
        for (double[] pt : points) {
            Vec3d from = new Vec3d(pt[0], pt[1], pt[2]);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(from, src,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, victim));
            if (hit.getType() == HitResult.Type.MISS) clear++;
        }
        return clear / (float) points.length;
    }

    public static void renderBlast(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        float hp = p.getHealth() + p.getAbsorptionAmount();
        List<Row> rows = new ArrayList<>();
        if (blasts.isEmpty()) {
            rows.add(Row.of("No crystals or anchors nearby", Hud.GRAY));
        } else {
            Blast worst = blasts.get(0);
            rows.add(Row.bar(String.format("Worst: %.1f / %.1f HP", worst.damage(), hp),
                    hp <= 0 ? 1f : worst.damage() / hp, worst.lethal() ? Hud.RED : worst.damage() > hp * 0.5f ? Hud.YELLOW : Hud.GREEN));
            if (worst.lethal()) rows.add(Row.of("LETHAL - take cover", Hud.RED));
            for (int i = 0; i < Math.min(4, blasts.size()); i++) {
                Blast b = blasts.get(i);
                rows.add(Row.of(String.format("%s %.1fm: %.1f dmg", b.label(), b.distance(), b.damage()), blastColor(b, hp)));
            }
        }
        Hud.panel(ctx, "Blast Damage", Hud.AQUA, rows.toArray(new Row[0]));

        // Mark each explosive on screen with the damage it would do to you.
        if (mc.options.getPerspective().isFirstPerson()) {
            for (Blast b : blasts) {
                int[] s = project(mc, ctx, b.pos().add(0.0, 1.2, 0.0));
                if (s == null) continue;
                String label = String.format("%.1f", b.damage());
                int color = blastColor(b, hp);
                int w = mc.textRenderer.getWidth(label);
                ctx.fill(s[0] - w / 2 - 3, s[1] - 2, s[0] + w / 2 + 3, s[1] + 10, 0xB0000000);
                ctx.fill(s[0] - w / 2 - 3, s[1] + 10, s[0] + w / 2 + 3, s[1] + 11, color);
                ctx.drawTextWithShadow(mc.textRenderer, label, s[0] - w / 2, s[1], color);
            }
        }
    }

    private static int blastColor(Blast b, float hp) {
        return b.lethal() ? Hud.RED : b.damage() > hp * 0.5f ? Hud.YELLOW : Hud.GREEN;
    }

    /** Estimated damage of your next hit on {@code target} (no enchantments), for the Target HUD. */
    public static float estimateHit(ClientPlayerEntity p, LivingEntity target) {
        float base = (float) p.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        float charge = p.getAttackCooldownProgress(0.5f);
        float damage = base * (0.2f + charge * charge * 0.8f);
        boolean crit = p.fallDistance > 0.0f && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && charge > 0.9f && !p.isSprinting();
        if (crit) damage *= 1.5f;
        return Math.max(0f, afterArmor(target, damage));
    }

    // =====================================================================
    // Crosshair extras (attack-cooldown arc, shield-down alert)
    // =====================================================================

    public static void crosshairExtras(DrawContext ctx, MinecraftClient mc, int cx, int cy, int radius) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        Config c = Config.get();
        if (c.crosshairCooldownArc) {
            float progress = p.getAttackCooldownProgress(0f);
            if (progress < 1f) {
                int color = progress >= 0.9f ? Hud.GREEN : Hud.YELLOW;
                int steps = Math.max(40, radius * 6);
                for (int i = 0; i < steps; i++) {
                    if (i / (float) steps > progress) break;
                    double a = -Math.PI / 2.0 + i * (Math.PI * 2.0 / steps);
                    int x = cx + (int) Math.round(Math.cos(a) * radius);
                    int y = cy + (int) Math.round(Math.sin(a) * radius);
                    ctx.fill(x, y, x + 1, y + 1, color);
                }
            }
        }
        if (c.crosshairShieldAlert) {
            long left = stunUntil - System.currentTimeMillis();
            if (left > 0L) {
                String text = String.format("SHIELD DOWN %.1fs", left / 1000.0);
                ctx.drawTextWithShadow(mc.textRenderer, text, cx - mc.textRenderer.getWidth(text) / 2, cy + radius + 8, Hud.GOLD);
            }
        }
    }

    // =====================================================================
    // Projectile & pearl predictor
    // =====================================================================

    private static final class Path {
        final List<Vec3d> points = new ArrayList<>();
        Vec3d landing;
        int ticks;
    }

    private static Path simulate(MinecraftClient mc, Entity ignore, Vec3d start, Vec3d velocity, double gravity, int maxTicks) {
        Path path = new Path();
        Vec3d pos = start, vel = velocity;
        path.points.add(pos);
        for (int i = 0; i < maxTicks; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ignore));
            path.ticks++;
            if (hit.getType() != HitResult.Type.MISS) {
                path.landing = hit.getPos();
                path.points.add(hit.getPos());
                return path;
            }
            pos = next;
            path.points.add(pos);
            vel = vel.multiply(0.99).subtract(0.0, gravity, 0.0);
            if (pos.y < mc.world.getBottomY() - 16) break;
        }
        return path;
    }

    private static boolean isThrowable(ItemStack s) {
        return s.isOf(Items.ENDER_PEARL) || s.isOf(Items.SNOWBALL) || s.isOf(Items.EGG)
                || s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION);
    }

    public static void renderTrajectory(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        if (!mc.options.getPerspective().isFirstPerson()) return;

        // Your own throw or bow shot.
        Vec3d pv = p.getVelocity();
        Vec3d inherit = new Vec3d(pv.x, p.isOnGround() ? 0.0 : pv.y, pv.z);
        Path own = null;
        ItemStack held = isThrowable(p.getMainHandStack()) ? p.getMainHandStack()
                : isThrowable(p.getOffHandStack()) ? p.getOffHandStack() : null;
        if (held != null) {
            boolean potion = held.isOf(Items.SPLASH_POTION) || held.isOf(Items.LINGERING_POTION);
            double speed = potion ? 0.5 : 1.5;
            double gravity = potion ? 0.05 : 0.03;
            float pitch = p.getPitch() + (potion ? -20f : 0f);
            Vec3d vel = Vec3d.fromPolar(pitch, p.getYaw()).multiply(speed).add(inherit);
            own = simulate(mc, p, new Vec3d(p.getX(), p.getEyeY() - 0.1, p.getZ()), vel, gravity, 200);
        } else if (p.isUsingItem() && p.getActiveItem().isOf(Items.BOW)) {
            float pull = BowItem.getPullProgress(p.getItemUseTime());
            if (pull > 0.1f) {
                Vec3d vel = Vec3d.fromPolar(p.getPitch(), p.getYaw()).multiply(pull * 3.0).add(inherit);
                own = simulate(mc, p, new Vec3d(p.getX(), p.getEyeY() - 0.1, p.getZ()), vel, 0.05, 200);
            }
        }
        if (own != null) drawPath(ctx, mc, own, Hud.AQUA, p.getEyePos(), null);

        // Enemy pearls in flight, with landing spot, distance and time.
        for (EnderPearlEntity pearl : mc.world.getEntitiesByClass(EnderPearlEntity.class,
                p.getBoundingBox().expand(96.0), e -> true)) {
            Entity owner = pearl.getOwner();
            if (owner == p) continue;
            Path path = simulate(mc, pearl, pearl.getPos(), pearl.getVelocity(), 0.03, 200);
            String who = owner == null ? "Pearl" : owner.getName().getString();
            drawPath(ctx, mc, path, Hud.RED, p.getEyePos(), who);
        }
    }

    private static void drawPath(DrawContext ctx, MinecraftClient mc, Path path, int color, Vec3d from, String label) {
        int[] prev = null;
        for (int i = 0; i < path.points.size(); i++) {
            int[] s = project(mc, ctx, path.points.get(i));
            if (s != null && prev != null) line(ctx, prev[0], prev[1], s[0], s[1], (color & 0x00FFFFFF) | 0xB0000000);
            prev = s;
        }
        if (path.landing == null) return;
        int[] l = project(mc, ctx, path.landing);
        if (l == null) return;
        ctx.fill(l[0] - 4, l[1], l[0] + 5, l[1] + 1, color);
        ctx.fill(l[0], l[1] - 4, l[0] + 1, l[1] + 5, color);
        double distance = from.distanceTo(path.landing);
        String text = String.format("%s%.1fm  %.1fs", label == null ? "" : label + "  ", distance, path.ticks / 20.0);
        ctx.fill(l[0] + 6, l[1] - 5, l[0] + 10 + mc.textRenderer.getWidth(text), l[1] + 6, 0xA0000000);
        ctx.drawTextWithShadow(mc.textRenderer, text, l[0] + 8, l[1] - 4, color);
    }

    // =====================================================================
    // Hitboxes and eye-level lasers (restricted)
    // =====================================================================

    private static final int[][] BOX_EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    public static void renderHitboxes(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p) {
        if (!mc.options.getPerspective().isFirstPerson()) return;
        for (PlayerEntity other : mc.world.getPlayers()) {
            if (other == p || other.squaredDistanceTo(p) > 40.0 * 40.0) continue;
            Box b = other.getBoundingBox();
            int[][] corners = new int[8][];
            boolean ok = true;
            for (int i = 0; i < 8 && ok; i++) {
                corners[i] = project(mc, ctx, new Vec3d((i & 1) == 0 ? b.minX : b.maxX,
                        (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ));
                ok = corners[i] != null;
            }
            if (ok) {
                int color = p.distanceTo(other) <= 3.0f ? 0xCC72E0B0 : 0xB0FFFFFF;
                for (int[] edge : BOX_EDGES) {
                    line(ctx, corners[edge[0]][0], corners[edge[0]][1], corners[edge[1]][0], corners[edge[1]][1], color);
                }
            }
            // Eye-level laser along the opponent's look vector.
            Vec3d eye = other.getEyePos();
            Vec3d end = eye.add(other.getRotationVec(1.0f).multiply(8.0));
            int[] a = project(mc, ctx, eye);
            int[] z = project(mc, ctx, end);
            if (a != null && z != null) line(ctx, a[0], a[1], z[0], z[1], 0x90FF4A5E);
        }
    }

    // =====================================================================
    // Screen-space helpers (world position -> GUI coordinates)
    // =====================================================================

    /** Projects a world position onto the GUI, or null when it is behind the camera. First-person only. */
    public static int[] project(MinecraftClient mc, DrawContext ctx, Vec3d world) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return null;
        Vec3d cam = p.getEyePos();
        double yaw = Math.toRadians(p.getYaw());
        double pitch = Math.toRadians(p.getPitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

        double vx = world.x - cam.x, vy = world.y - cam.y, vz = world.z - cam.z;
        double z = vx * fx + vy * fy + vz * fz;
        if (z < 0.05) return null;
        double x = vx * rx + vz * rz;
        double y = vx * ux + vy * uy + vz * uz;

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = sw / (double) sh;
        int sx = (int) Math.round(sw / 2.0 + (x / z) / (tanHalf * aspect) * (sw / 2.0));
        int sy = (int) Math.round(sh / 2.0 - (y / z) / tanHalf * (sh / 2.0));
        return new int[]{sx, sy};
    }

    /** 1px line between two GUI points (clipped away when entirely off-screen). */
    public static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        if ((x0 < 0 && x1 < 0) || (y0 < 0 && y1 < 0) || (x0 > sw && x1 > sw) || (y0 > sh && y1 > sh)) return;
        if (Math.abs(x0) > 6000 || Math.abs(y0) > 6000 || Math.abs(x1) > 6000 || Math.abs(y1) > 6000) return;
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        for (int guard = 0; guard < 1600; guard++) {
            if (x0 >= 0 && y0 >= 0 && x0 < sw && y0 < sh) ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }
}
