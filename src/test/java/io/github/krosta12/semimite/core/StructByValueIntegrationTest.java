package io.github.krosta12.semimite.core;

import io.github.krosta12.semimite.annotation.MiteInline;
import io.github.krosta12.semimite.annotation.MiteStruct;
import io.github.krosta12.semimite.client.MiteClient;
import io.github.krosta12.semimite.compiler.CompilerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Compiles real C++ and compares the {@code @MiteInline} layout with sizeof(). */
class StructByValueIntegrationTest {

    @MiteStruct
    public static class Vec3 {
        public float x, y, z;
    }

    @MiteStruct
    public static class Particle {
        public int id;
        public float mass;
        @MiteInline
        public Vec3 pos;
    }

    @MiteStruct
    public static class Mesh {
        @MiteInline(length = 3)
        public Vec3[] points;
    }

    @MiteStruct
    public static class Samples {
        public int id;
        @MiteInline(length = 4)
        public double[] values;
    }

    @MiteClient
    public interface ParticleClient {
        long particle_size();

        float particle_sum(Particle p);

        void particle_shift(Particle p, float dx);

        long mesh_size();

        float mesh_corner_sum(Mesh m);

        long samples_size();

        double samples_edges(Samples s);

        void samples_scale_first(Samples s, double k);
    }

    static boolean compilerAvailable() {
        String path = System.getenv("PATH");
        if (path == null) return false;
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (String dir : path.split(File.pathSeparator)) {
            if (new File(dir, win ? "g++.exe" : "g++").isFile()) return true;
            if (new File(dir, win ? "clang++.exe" : "clang++").isFile()) return true;
        }
        return false;
    }

    @Test
    @EnabledIf("compilerAvailable")
    void inlineStructLayoutMatchesCompiler(@TempDir(cleanup = CleanupMode.ON_SUCCESS) Path tempDir) throws Exception {
        Path scriptsDir = tempDir.resolve("cppScripts");
        Files.createDirectories(scriptsDir);
        Files.writeString(scriptsDir.resolve("particle.cpp"), """
                struct Vec3 { float x, y, z; };
                struct Particle { int id; float mass; Vec3 pos; };
                struct Mesh { Vec3 points[3]; };
                struct Samples { int id; double values[4]; };

                extern "C" {

                // @mite
                unsigned long long particle_size() {
                    return sizeof(Particle);
                }

                // @mite
                float particle_sum(Particle* p) {
                    return p->pos.x + p->pos.y + p->pos.z;
                }

                // @mite
                void particle_shift(Particle* p, float dx) {
                    p->pos.x += dx;
                }

                // @mite
                unsigned long long mesh_size() {
                    return sizeof(Mesh);
                }

                // @mite
                float mesh_corner_sum(Mesh* m) {
                    return m->points[0].x + m->points[1].y + m->points[2].z;
                }

                // @mite
                unsigned long long samples_size() {
                    return sizeof(Samples);
                }

                // @mite
                double samples_edges(Samples* s) {
                    return s->values[0] + s->values[3];
                }

                // @mite
                void samples_scale_first(Samples* s, double k) {
                    s->values[0] *= k;
                }

                }
                """);

        CompilerConfig config = new CompilerConfig(
                scriptsDir,
                tempDir.resolve(".mite-cache"),
                null,
                java.util.List.of("-O2"),
                (int) java.lang.foreign.ValueLayout.ADDRESS.byteSize()
        );

        try (MiteBootstrap bootstrap = new MiteBootstrap();
             MiteContext context = bootstrap.start("io.github.krosta12.semimite.core", config)) {
            ParticleClient client = context.get(ParticleClient.class);

            assertEquals(20L, client.particle_size(),
                    "Java layout must be byte-identical to sizeof(Particle) reported by the compiler");

            Particle p = new Particle();
            p.id = 1;
            p.mass = 2f;
            p.pos = new Vec3();
            p.pos.x = 1f;
            p.pos.y = 2f;
            p.pos.z = 3f;

            assertEquals(6f, client.particle_sum(p), 1e-6f,
                    "C++ must read the inline Vec3 at offsets 8/12/16");

            client.particle_shift(p, 10f);
            assertEquals(11f, p.pos.x, 1e-6f,
                    "copy-back must reach the inline nested object");

            assertEquals(36L, client.mesh_size(),
                    "Java layout must be byte-identical to sizeof(Mesh) reported by the compiler");

            Mesh mesh = new Mesh();
            mesh.points = new Vec3[]{new Vec3(), new Vec3(), new Vec3()};
            mesh.points[0].x = 1f;
            mesh.points[1].y = 2f;
            mesh.points[2].z = 3f;

            assertEquals(6f, client.mesh_corner_sum(mesh), 1e-6f,
                    "a by-value Vec3 array must be one contiguous block with a 12-byte stride");

            assertEquals(40L, client.samples_size(),
                    "Java layout must be byte-identical to sizeof(Samples) reported by the compiler");

            Samples samples = new Samples();
            samples.id = 5;
            samples.values = new double[]{1.5, 0, 0, 2.5};

            assertEquals(4.0, client.samples_edges(samples), 1e-9,
                    "a by-value double array must start at offset 8");

            client.samples_scale_first(samples, 2.0);
            assertEquals(3.0, samples.values[0], 1e-9,
                    "copy-back must reach the inline primitive array");
        }
    }
}
