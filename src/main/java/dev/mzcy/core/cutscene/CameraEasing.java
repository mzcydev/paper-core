package dev.mzcy.core.cutscene;

/**
 * Easing functions for smooth camera interpolation between
 * {@link CameraPoint}s on a {@link CameraPath}.
 *
 * <p>Each function maps a linear progress value {@code t ∈ [0,1]}
 * to an eased value {@code t' ∈ [0,1]}.
 */
public enum CameraEasing {

    /**
     * Constant speed — no easing.
     */
    LINEAR {
        @Override
        public double ease(double t) {
            return t;
        }
    },

    /**
     * Slow start, fast end.
     */
    EASE_IN {
        @Override
        public double ease(double t) {
            return t * t * t;
        }
    },

    /**
     * Fast start, slow end.
     */
    EASE_OUT {
        @Override
        public double ease(double t) {
            return 1 - Math.pow(1 - t, 3);
        }
    },

    /**
     * Slow start and end, fast middle.
     */
    EASE_IN_OUT {
        @Override
        public double ease(double t) {
            return t < 0.5
                    ? 4 * t * t * t
                    : 1 - Math.pow(-2 * t + 2, 3) / 2;
        }
    },

    /**
     * Overshoots slightly at the end (elastic bounce).
     */
    EASE_OUT_BACK {
        private static final double C1 = 1.70158;
        private static final double C3 = C1 + 1;

        @Override
        public double ease(double t) {
            return 1 + C3 * Math.pow(t - 1, 3) + C1 * Math.pow(t - 1, 2);
        }
    },

    /**
     * Smooth sinusoidal curve.
     */
    SINE {
        @Override
        public double ease(double t) {
            return -(Math.cos(Math.PI * t) - 1) / 2;
        }
    };

    /**
     * Applies this easing function to a linear progress value.
     *
     * @param t linear progress in [0, 1]
     * @return eased progress in [0, 1]
     */
    public abstract double ease(double t);
}