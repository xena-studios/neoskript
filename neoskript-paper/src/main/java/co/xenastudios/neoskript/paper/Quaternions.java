/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Parameter;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

/** Skript's quaternions: rotations, as displays use them. */
final class Quaternions {

    private Quaternions() {}

    /** Degrees in radians, worked out in floats as Skript does, so rotations come out exactly the same. */
    static float radians(double degrees) {
        return (float) (degrees / 180) * (float) Math.PI;
    }

    /** Radians in degrees, without the last float digit's error: 45, not 45.000004. */
    static double degrees(float radians) {
        return Math.round((double) (radians / (float) Math.PI * 180f) * 10_000d) / 10_000d;
    }

    static void register(Registrar registrar) {
        registrar.type("quaternion", "quaternion", Quaternionf.class);
        registrar.text(Quaternionf.class, q -> "w:" + Values.decimal(q.w) + ", x:" + Values.decimal(q.x) + ", y:"
                + Values.decimal(q.y) + ", z:" + Values.decimal(q.z));
        // Quaternions change in place, so variables get copies.
        registrar.cloner(Quaternionf.class, Quaternionf::new);
        // Equal when each part is: 0 equals -0, and not-a-number equals nothing.
        registrar.comparison(Quaternionf.class, Quaternionf.class, (a, b) -> a.w == b.w && a.x == b.x && a.y == b.y && a.z == b.z);

        registrar.function("quaternion", List.of(Parameter.of("w", "number"), Parameter.of("x", "number"),
                Parameter.of("y", "number"), Parameter.of("z", "number")), "quaternion", false, args -> {
                    for (Object arg : args) {
                        if (!(arg instanceof Number)) {
                            return null;
                        }
                    }
                    return new Quaternionf(((Number) args[1]).floatValue(), ((Number) args[2]).floatValue(),
                            ((Number) args[3]).floatValue(), ((Number) args[0]).floatValue());
                });
        // The rotation by an angle around an axis, which isn't made a unit vector: axisAngle(100, vector(2, 3, 4)).
        registrar.function("axisAngle", List.of(Parameter.of("angle", "number"), Parameter.of("axis", "vector")),
                "quaternion", false, args -> {
                    if (!(args[0] instanceof Number angle) || !(args[1] instanceof Vector axis)
                            || !Double.isFinite(angle.doubleValue()) || !Double.isFinite(axis.getX())
                            || !Double.isFinite(axis.getY()) || !Double.isFinite(axis.getZ())) {
                        return null;
                    }
                    return new Quaternionf().setAngleAxis(radians(angle.doubleValue()), (float) axis.getX(),
                            (float) axis.getY(), (float) axis.getZ());
                });

        // The angle and axis a quaternion rotates by; changing one keeps the other.
        registrar.expression("ExprQuaternionAxisAngle", "object", info -> new AxisAngle(info.hasTag("axis")),
                "[the] rotation (angle|axis:axis) of %quaternions%", "%quaternions%'[s] rotation (angle|axis:axis)");

        registrar.expression("ExprRotate", "object", info -> {
            int pattern = info.pattern();
            char axis = info.hasTag("x") ? 'x' : info.hasTag("y") ? 'y' : 'z';
            boolean local = info.hasTag("local");
            return (context, args) -> {
                List<Object> rotated = new ArrayList<>();
                for (Object value : (Object[]) args[0]) {
                    Object copy = switch (value) {
                        case Vector vector -> vector.clone();
                        case Quaternionf quaternion -> new Quaternionf(quaternion);
                        default -> null;
                    };
                    if (copy != null && rotate(copy, pattern, axis, local, args)) {
                        rotated.add(copy);
                    }
                }
                return rotated.size() == 1 ? rotated.getFirst() : rotated.toArray();
            };
        }, "%vectors/quaternions% rotated around [the] [local:local] (x:x|y:y|z:z)(-| )axis by %number% [degrees]",
                "%quaternions% rotated by x %number%, y %number%(, [and]| and) z %number%");
        registrar.effect("EffRotate", info -> {
            int pattern = info.pattern();
            char axis = info.hasTag("x") ? 'x' : info.hasTag("y") ? 'y' : 'z';
            boolean local = info.hasTag("local");
            return (context, args) -> {
                for (Object value : (Object[]) args[0]) {
                    if (value instanceof Display display) {
                        // A display turns by its left rotation.
                        Transformation t = display.getTransformation();
                        Quaternionf left = new Quaternionf(t.getLeftRotation());
                        if (rotate(left, pattern, axis, local, args)) {
                            display.setTransformation(new Transformation(t.getTranslation(), left, t.getScale(),
                                    t.getRightRotation()));
                        }
                    } else {
                        rotate(value, pattern, axis, local, args);
                    }
                }
            };
        }, "rotate %vectors/quaternions/entities% around [the] [local:local] (x:x|y:y|z:z)(-| )axis by %number% [degrees]",
                "rotate %quaternions/entities% by x %number%, y %number%(, [and]| and) z %number%");

        // How far a display is moved from where it is, and how much it's stretched.
        registrar.expression("ExprDisplayTransformationScaleTranslation", "vector", info -> {
            boolean translation = info.hasTag("translation");
            return new SettableProperty<>(Display.class, Vector.class, display -> {
                org.joml.Vector3f v = translation ? display.getTransformation().getTranslation()
                        : display.getTransformation().getScale();
                return new Vector(v.x, v.y, v.z);
            }, (display, vector) -> setVector(display, translation, vector))
                    .resettingTo((java.util.function.Consumer<Object>) display -> setVector((Display) display, translation,
                            translation ? new Vector() : new Vector(1, 1, 1)));
        }, "[the] (display|[display] transformation) (scale|translation:translation)[s] of %entities%",
                "%entities%'[s] (display|[display] transformation) (scale|translation:translation)[s]");
        registrar.expression("ExprDisplayRotation", "quaternion", info -> {
            boolean right = info.hasTag("right");
            return new SettableProperty<>(Display.class, Quaternionf.class, display -> new Quaternionf(right
                    ? display.getTransformation().getRightRotation() : display.getTransformation().getLeftRotation()),
                    (display, rotation) -> setRotation(display, right, rotation))
                    .resettingTo((java.util.function.Consumer<Object>) display -> setRotation((Display) display, right,
                            new Quaternionf()));
        }, "[the] (left|right:right) [transformation] rotation[s] of %entities%",
                "%entities%'[s] (left|right:right) [transformation] rotation[s]");
    }

    /** Sets a display's translation or scale, if every part of it is a number. */
    private static void setVector(Display display, boolean translation, Vector vector) {
        if (!Double.isFinite(vector.getX()) || !Double.isFinite(vector.getY()) || !Double.isFinite(vector.getZ())) {
            return;
        }
        org.joml.Vector3f v = new org.joml.Vector3f((float) vector.getX(), (float) vector.getY(), (float) vector.getZ());
        Transformation t = display.getTransformation();
        display.setTransformation(new Transformation(translation ? v : t.getTranslation(), t.getLeftRotation(),
                translation ? t.getScale() : v, t.getRightRotation()));
    }

    /** Sets a display's left or right rotation, if every part of it is a number. */
    private static void setRotation(Display display, boolean right, Quaternionf rotation) {
        if (!Float.isFinite(rotation.w) || !Float.isFinite(rotation.x) || !Float.isFinite(rotation.y)
                || !Float.isFinite(rotation.z)) {
            return;
        }
        Transformation t = display.getTransformation();
        display.setTransformation(new Transformation(t.getTranslation(), right ? t.getLeftRotation() : new Quaternionf(rotation),
                t.getScale(), right ? new Quaternionf(rotation) : t.getRightRotation()));
    }

    /**
     * Rotates {@code value} in place: around an axis by an angle (pattern 0), or by x, y and z angles (pattern 1).
     * Around a global axis is the rotation after the quaternion's; around a local one, before it.
     */
    private static boolean rotate(Object value, int pattern, char axis, boolean local, Object[] args) {
        if (pattern == 1) {
            if (!(value instanceof Quaternionf quaternion) || !(args[1] instanceof Number x) || !(args[2] instanceof Number y)
                    || !(args[3] instanceof Number z)) {
                return false;
            }
            quaternion.rotateZYX(radians(z.doubleValue()), radians(y.doubleValue()), radians(x.doubleValue()));
            return true;
        }
        if (!(args[1] instanceof Number by)) {
            return false;
        }
        switch (value) {
            case Vector vector -> {
                // A vector has no local axes to turn around.
                if (!local) {
                    double angle = Math.toRadians(by.doubleValue());
                    switch (axis) {
                        case 'x' -> vector.rotateAroundX(angle);
                        case 'y' -> vector.rotateAroundY(angle);
                        default -> vector.rotateAroundZ(angle);
                    }
                }
                return true;
            }
            case Quaternionf quaternion -> {
                float angle = radians(by.doubleValue());
                UnaryOperator<Quaternionf> turn = switch (axis) {
                    case 'x' -> q -> local ? q.rotateX(angle) : q.rotateLocalX(angle);
                    case 'y' -> q -> local ? q.rotateY(angle) : q.rotateLocalY(angle);
                    default -> q -> local ? q.rotateZ(angle) : q.rotateLocalZ(angle);
                };
                turn.apply(quaternion);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** {@code the rotation angle of %quaternions%} in degrees, or its axis. */
    private record AxisAngle(boolean axis) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object value : (Object[]) arguments[0]) {
                if (value instanceof Quaternionf quaternion) {
                    AxisAngle4f angle = new AxisAngle4f().set(quaternion);
                    values.add(axis ? new Vector(angle.x, angle.y, angle.z) : (Object) degrees(angle.angle));
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {axis ? Vector.class : Number.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta == null || delta.length == 0) {
                return;
            }
            for (Object value : (Object[]) arguments[0]) {
                if (value instanceof Quaternionf quaternion) {
                    AxisAngle4f angle = new AxisAngle4f().set(quaternion);
                    if (axis && delta[0] instanceof Vector vector) {
                        angle.x = (float) vector.getX();
                        angle.y = (float) vector.getY();
                        angle.z = (float) vector.getZ();
                    } else if (!axis && delta[0] instanceof Number degrees) {
                        angle.angle = radians(degrees.doubleValue());
                    }
                    quaternion.setAngleAxis(angle.angle, angle.x, angle.y, angle.z);
                }
            }
        }
    }
}
