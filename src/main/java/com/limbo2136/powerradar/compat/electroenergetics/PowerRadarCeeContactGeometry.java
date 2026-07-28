package com.limbo2136.powerradar.compat.electroenergetics;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Единое место настройки положения электрических контактов отдельных блоков.
 *
 * <p>Все размеры ниже задаются в пикселях Blockbench относительно центра блока.
 * Плюс всегда становится узлом 0, минус — узлом 1.</p>
 */
public final class PowerRadarCeeContactGeometry {
    // Горизонтальные блоки: Y, смещение к задней стороне, смещения плюса и минуса вправо.
    private static final HorizontalPair RADAR_CONTROLLER = new HorizontalPair(5.0, 9.0, 2.0, -4.0);
    private static final HorizontalPair RADAR_MONITOR = new HorizontalPair(11.52, 8.96, 4.0, -4.0);
    private static final HorizontalPair LOGIC_DOCK = new HorizontalPair(3.0, 3.0, 9.0, -9.0);
    private static final HorizontalPair SHELL_ALARM = new HorizontalPair(10.88, 8.96, 3.84, -3.84);
    private static final HorizontalPair ONBOARD_COMPUTER = new HorizontalPair(8.0, 4.0, 8.96, -8.96);

    // Блоки, устанавливаемые на любую грань: наружу, плюс вправо, минус вправо, вверх.
    private static final FacePair TARGET_CONTROLLER = new FacePair(8.96, 3.84, -3.84, -3.52);
    private static final FacePair INTERCEPTION_CONTROLLER = new FacePair(8.96, 3.84, -3.84, 3.52);

    private PowerRadarCeeContactGeometry() {
    }

    public static PowerRadarCeeTerminalPair radarController(Direction facing) {
        return RADAR_CONTROLLER.resolve(facing);
    }

    public static PowerRadarCeeTerminalPair radarMonitor(Direction facing) {
        return RADAR_MONITOR.resolve(facing);
    }

    public static PowerRadarCeeTerminalPair logicDock(Direction facing) {
        return LOGIC_DOCK.resolve(facing);
    }

    public static PowerRadarCeeTerminalPair shellAlarm(Direction facing) {
        return SHELL_ALARM.resolve(facing);
    }

    public static PowerRadarCeeTerminalPair onboardComputer(Direction facing) {
        return ONBOARD_COMPUTER.resolve(facing);
    }

    public static PowerRadarCeeTerminalPair targetController(Direction facing) {
        return TARGET_CONTROLLER.resolve(facing);
    }

    public static PowerRadarCeeTerminalPair interceptionController(Direction facing) {
        return INTERCEPTION_CONTROLLER.resolve(facing);
    }

    /**
     * Преобразует модельные пиксели горизонтального блока в локальные координаты CEE.
     * FACING указывает лицевую сторону, поэтому контакты смещаются к противоположной стороне.
     */
    private record HorizontalPair(
            double yPixels,
            double rearPixels,
            double positiveRightPixels,
            double negativeRightPixels
    ) {
        private PowerRadarCeeTerminalPair resolve(Direction facing) {
            Direction rear = facing.getOpposite();
            Direction right = modelRightOf(facing);
            Vec3 center = new Vec3(0.5, pixels(this.yPixels), 0.5)
                    .add(scale(rear, pixels(this.rearPixels)));
            return new PowerRadarCeeTerminalPair(
                    center.add(scale(right, pixels(this.positiveRightPixels))),
                    center.add(scale(right, pixels(this.negativeRightPixels))));
        }
    }

    /**
     * Преобразует модельные пиксели блока с шестью вариантами установки.
     * Локальные направления вправо и вверх выбираются устойчиво и для пола, и для потолка.
     */
    private record FacePair(
            double outwardPixels,
            double positiveRightPixels,
            double negativeRightPixels,
            double upPixels
    ) {
        private PowerRadarCeeTerminalPair resolve(Direction facing) {
            Direction face = facing.getOpposite();
            Direction right = rightOf(facing);
            Direction up = upOf(facing);
            Vec3 center = new Vec3(0.5, 0.5, 0.5)
                    .add(scale(face, pixels(this.outwardPixels)))
                    .add(scale(up, pixels(this.upPixels)));
            return new PowerRadarCeeTerminalPair(
                    center.add(scale(right, pixels(this.positiveRightPixels))),
                    center.add(scale(right, pixels(this.negativeRightPixels))));
        }
    }

    private static double pixels(double value) {
        return value / 16.0;
    }

    private static Vec3 scale(Direction direction, double distance) {
        return new Vec3(
                direction.getStepX() * distance,
                direction.getStepY() * distance,
                direction.getStepZ() * distance);
    }

    private static Direction rightOf(Direction facing) {
        return facing.getAxis() == Direction.Axis.Y ? Direction.WEST : modelRightOf(facing);
    }

    private static Direction modelRightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static Direction upOf(Direction facing) {
        return switch (facing) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
    }
}
