package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class RangeBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargets = settings.createGroup("Targets");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away targets are searched for.")
        .defaultValue(30)
        .min(1).sliderMax(100)
        .build());

    private final Setting<Boolean> swordOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("sword-only")
        .description("Only runs while you hold a sword.")
        .defaultValue(true)
        .build());

    private final Setting<Double> hopDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("hop-distance")
        .description("Max blocks moved per tick. Keep under 10 or servers will rubber-band you.")
        .defaultValue(8)
        .min(1).sliderMax(9.5)
        .build());

    private final Setting<Double> stopDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("stop-distance")
        .description("How close to the target you stop (vanilla reach is about 3).")
        .defaultValue(2.5)
        .min(1).sliderMax(3)
        .build());

    private final Setting<Boolean> players = sgTargets.add(new BoolSetting.Builder()
        .name("players").description("Target other players.").defaultValue(false).build());

    private final Setting<Boolean> ignoreFriends = sgTargets.add(new BoolSetting.Builder()
        .name("ignore-friends").description("Never target players on your friends list.").defaultValue(true).build());

    private final Setting<Boolean> hostiles = sgTargets.add(new BoolSetting.Builder()
        .name("hostile-mobs").description("Target zombies, skeletons, creepers, etc.").defaultValue(true).build());

    private final Setting<Boolean> npcs = sgTargets.add(new BoolSetting.Builder()
        .name("npcs").description("Target villagers and wandering traders.").defaultValue(false).build());

    private final Setting<Boolean> animals = sgTargets.add(new BoolSetting.Builder()
        .name("animals").description("Target cows, pigs, sheep, etc.").defaultValue(false).build());

    public RangeBot() {
        super(Categories.COMBAT, "range-bot", "Teleports to nearby targets and kills them while you hold a sword.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (swordOnly.get() && !mc.player.getMainHandItem().is(ItemTags.SWORDS)) return;

        Entity target = findTarget();
        if (target == null) return;

        double dist = mc.player.distanceTo(target);
        if (dist > stopDistance.get()) {
            Vec3 from = mc.player.position();
            Vec3 to = target.position();
            Vec3 dir = to.subtract(from).normalize();
            double step = Math.min(hopDistance.get(), dist - stopDistance.get());
            Vec3 next = from.add(dir.scale(step));
            mc.player.setPos(next.x, next.y, next.z);
            mc.player.setDeltaMovement(Vec3.ZERO);
        }

        if (mc.player.distanceTo(target) <= 3.5 && mc.player.getAttackStrengthScale(0.5f) >= 1f) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private Entity findTarget() {
        List<Entity> found = mc.level.getEntities(
            (Entity) null,
            mc.player.getBoundingBox().inflate(range.get()),
            this::isValid
        );
        return found.stream()
            .min(Comparator.comparingDouble(e -> mc.player.distanceTo(e)))
            .orElse(null);
    }

    private boolean isValid(Entity e) {
        if (e == mc.player || !(e instanceof LivingEntity living) || !living.isAlive()) return false;
        if (mc.player.distanceTo(e) > range.get()) return false;

        if (e instanceof Player p) {
            if (!players.get()) return false;
            return !(ignoreFriends.get() && Friends.get().isFriend(p));
        }
        if (e instanceof Enemy) return hostiles.get();
        if (e instanceof Npc) return npcs.get();
        if (e instanceof Animal) return animals.get();
        return false;
    }
}
