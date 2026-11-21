package technograpple;

import java.lang.reflect.Method;
import java.util.function.DoubleSupplier;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;

public final class IrisSunCompatibility {
    private static final String SCALE_UNIFORM = "technograppleSunScale";
    private static final Method PACK_ACTIVE = findPackActive();
    private static final String PHOTON_DRAW = "vec3 draw_sun(vec3 ray_dir) {";
    private static final String PHOTON_RADIUS = "sun_angular_radius - fast_acos(nu)";
    private static boolean warned;

    private IrisSunCompatibility() {}

    private static Method findPackActive() {
        if (!ModList.get().isLoaded("iris")) return null;
        try {
            return Class.forName("net.irisshaders.iris.Iris").getMethod("isPackInUseQuick");
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }

    static boolean shadersActive() {
        if (PACK_ACTIVE == null) return false;
        try {
            return (boolean) PACK_ACTIVE.invoke(null);
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }

    static float pathRotation() {
        if (!ModList.get().isLoaded("iris")) return 0;
        try {
            var iris = Class.forName("net.irisshaders.iris.Iris");
            var manager = iris.getMethod("getPipelineManager").invoke(null);
            var pipeline = manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            return pipeline == null ? 0 : (float) pipeline.getClass().getMethod("getSunPathRotation").invoke(pipeline);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            warn(unavailable);
            return 0;
        }
    }

    public static boolean keepsShaderMoon() {
        String name = activePackName();
        return name.startsWith("photon") || name.startsWith("bsl");
    }

    private static String activePackName() {
        if (!shadersActive()) return "";
        try {
            var iris = Class.forName("net.irisshaders.iris.Iris");
            String name = (String) iris.getMethod("getCurrentPackName").invoke(null);
            return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            warn(unavailable);
            return "";
        }
    }

    static SunView sunView() {
        float rotation = pathRotation();
        String packName = activePackName();
        boolean photon = packName.startsWith("photon");
        boolean bsl = packName.startsWith("bsl");
        boolean complementary = packName.startsWith("complementary");
        if (!photon && !bsl && !complementary) return new SunView(rotation, false, false);
        boolean bslProcedural = false;
        boolean round = false;
        try {
            var iris = Class.forName("net.irisshaders.iris.Iris");
            var pack = ((java.util.Optional<?>) iris.getMethod("getCurrentPack").invoke(null)).orElse(null);
            if (pack == null) return new SunView(rotation, false, false);
            var options = pack.getClass().getMethod("getShaderPackOptions").invoke(pack);
            var values = options.getClass().getMethod("getOptionValues").invoke(options);
            var type = Class.forName("net.irisshaders.iris.shaderpack.option.values.OptionValues");
            if (photon) {
                var getBoolean = type.getMethod("getBooleanValueOrDefault", String.class);
                round = !(boolean) getBoolean.invoke(values, "VANILLA_SUN");
            } else if (bsl) {
                var getBoolean = type.getMethod("getBooleanValueOrDefault", String.class);
                bslProcedural = (boolean) getBoolean.invoke(values, "SHADER_SUN_MOON");
                round = bslProcedural && "0".equals(type.getMethod("getStringValueOrDefault", String.class)
                        .invoke(values, "SHADER_SUN_MOON_SHAPE"));
            } else {
                var getString = type.getMethod("getStringValueOrDefault", String.class);
                var style = getString.invoke(values, "SUN_MOON_STYLE_DEFINE");
                round = "2".equals(style) || "3".equals(style)
                        || ("-1".equals(style) && "4".equals(getString.invoke(values, "SHADER_STYLE")));
            }
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            warn(unavailable);
        }
        return new SunView(rotation, bslProcedural, round);
    }

    public static String adaptShaderSource(String source) {
        if (source == null || source.contains(SCALE_UNIFORM)) return source;
        String complementaryDisk = "if (absVdotS > sunSizeFactor1) {";
        if (source.contains(complementaryDisk) && source.contains("moonCrescentOffset")
                && source.contains("float absVdotS = abs(VdotS);")) {
            String main = "void main() {";
            if (!source.contains(main)) return source;
            return source.replace(main, "uniform float " + SCALE_UNIFORM + ";\n" + main)
                    .replace(complementaryDisk, """
                            if (VdotS > 0.0 && technograppleSunScale > 1.0) {
                                sunSizeFactor1 = cos(min(1.56, acos(sunSizeFactor1) * technograppleSunScale));
                                sunSizeFactor2 = 1.0 / (1.0 - sunSizeFactor1);
                            }
                            if (absVdotS > sunSizeFactor1) {""");
        }
        String bslDraw = "void ShaderSunMoon(inout vec3 color, vec3 viewPos, vec3 sunColor, vec3 moonColor) {";
        if (source.contains(bslDraw) && source.contains("float isMoon = float(VoL < 0.0);")) {
            return source.replace(bslDraw, "uniform float " + SCALE_UNIFORM + ";\n" + bslDraw)
                    .replace("float sun = pow(smoothstep(1.0 - sunMoonSize", """
                            if (isMoon < 0.5 && technograppleSunScale > 1.0) {
                                sunMoonSize = 1.0 - cos(min(1.56, acos(1.0 - sunMoonSize) * technograppleSunScale));
                            }
                            float sun = pow(smoothstep(1.0 - sunMoonSize""")
                    .replace("vec2 sdfCoord = abs(vec2(VoLt, VoLb) / sunMoonSize", """
                            if (isMoon < 0.5) sunMoonSize *= max(1.0, technograppleSunScale);
                            vec2 sdfCoord = abs(vec2(VoLt, VoLb) / sunMoonSize""");
        }
        if (!source.contains("sun_luminance * sun_color * step")) return source;
        int radius = source.indexOf(PHOTON_RADIUS);
        if (radius < 0) return source;
        int draw = source.lastIndexOf(PHOTON_DRAW, radius);
        if (draw < 0) return source;
        int body = draw + PHOTON_DRAW.length();
        return source.substring(0, draw) + "uniform float " + SCALE_UNIFORM + ";\n"
                + PHOTON_DRAW
                + source.substring(body, radius) + "atan(tan(sun_angular_radius) * max(1.0, "
                + SCALE_UNIFORM + ")) - fast_acos(nu)" + source.substring(radius + PHOTON_RADIUS.length());
    }

    public static void registerUniform(Object uniforms) {
        try {
            UniformRegistration.method.invoke(uniforms, UniformRegistration.perFrame, SCALE_UNIFORM,
                    (DoubleSupplier) SunEffects::photonScale);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            warn(unavailable);
        }
    }

    private static void warn(Throwable unavailable) {
        if (warned) return;
        warned = true;
        LogUtils.getLogger().warn("TechnoGrapple could not access Iris sun compatibility hooks", unavailable);
    }

    private static final class UniformRegistration {
        private static final Method method;
        private static final Object perFrame;

        static {
            try {
                var frequency = Class.forName("net.irisshaders.iris.gl.uniform.UniformUpdateFrequency");
                var holder = Class.forName("net.irisshaders.iris.gl.uniform.UniformHolder");
                perFrame = frequency.getField("PER_FRAME").get(null);
                method = holder.getMethod("uniform1f", frequency, String.class, DoubleSupplier.class);
            } catch (ReflectiveOperationException unavailable) {
                throw new ExceptionInInitializerError(unavailable);
            }
        }
    }
}
