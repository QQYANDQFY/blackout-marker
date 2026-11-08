/*
 * Blackout Marker
 * Copyright (C) 2026 Blackout Marker contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package dev.blackoutmarker;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Draws the black square overlay with plain OpenGL after the level has been
 * rendered (and before the HUD). Occlusion is checked on the CPU with short
 * block-clip rays from the camera to the target's center/corners; this stays
 * reliable even when the depth texture is not available after post-processing.
 */
public final class OverlayRenderer {
    private static boolean initialized;
    private static boolean broken;
    private static boolean loggedFailure;

    private static int queryFbo = -1;
    private static int overlayFbo = -1;

    private static int cubeProgram = -1;
    private static int cubeVao = -1;
    private static int cubeVbo = -1;
    private static int cubeEbo = -1;

    private static int squareProgram = -1;
    private static int squareVao = -1;
    private static int squareVbo = -1;

    private static int uProj = -1;
    private static int uView = -1;
    private static int uCam = -1;
    private static int uCenter = -1;
    private static int uHalf = -1;
    private static int uViewport = -1;

    private static final Map<String, PendingQuery> pendingQueries = new HashMap<>();
    private static final Map<String, Boolean> visibleCache = new HashMap<>();

    private OverlayRenderer() {
    }

    public static void render(Minecraft minecraft) {
        if (broken || minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        List<TargetManager.Target> targets = BlackoutMarkerMod.targetManager().collect(minecraft);
        BlackoutConfig config = BlackoutMarkerMod.config();
        boolean hasSpecial = config.directionMarkerEnabled || config.sunMarkerEnabled || config.moonMarkerEnabled;
        if ((targets.isEmpty() && !hasSpecial) || !config.enabled) {
            return;
        }

        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        if (main == null) {
            return;
        }

        int colorTexture = textureId(main.getColorTexture());
        if (colorTexture <= 0) {
            failOnce("main render target is not using an OpenGL color texture", null);
            return;
        }

        CameraRenderState camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.projectionMatrix == null || camera.viewRotationMatrix == null) {
            return;
        }

        int width = main.width;
        int height = main.height;
        if (width <= 0 || height <= 0) {
            return;
        }

        GlState state = new GlState();
        try {
            ensureInitialized();
            if (broken) {
                return;
            }

            int visibleCount = updateVisibility(minecraft, targets, camera);
            if (visibleCount > 0 || hasSpecial) {
                drawSquares(minecraft, targets, camera, colorTexture, width, height);
            }
        } catch (Throwable t) {
            failOnce("overlay rendering failed", t);
        } finally {
            state.restore();
        }
    }

    private static int textureId(GpuTexture texture) {
        if (texture instanceof GlTexture glTexture) {
            return glTexture.glId();
        }
        return -1;
    }

    /**
     * Consumes queries that finished since the previous frame, publishes their
     * results to the targets, and submits this frame's queries. Returning the
     * number of targets with a known-visible result keeps the first frame from
     * drawing anything before the queries have resolved.
     */
    private static int updateVisibility(Minecraft minecraft,
                                        List<TargetManager.Target> targets,
                                        CameraRenderState camera) {
        int drawable = 0;
        for (TargetManager.Target target : targets) {
            if (!isInFrustum(camera, target) || !isInFront(camera, target.center)) {
                target.visible = false;
                continue;
            }

            if (!target.hideWhenOccluded) {
                target.visible = true;
                drawable++;
                continue;
            }

            target.visible = target.centerMode
                    ? isCenterVisible(minecraft, target, camera)
                    : isAnyPartVisible(minecraft, target, camera);
            if (target.visible) {
                drawable++;
            }
        }
        return drawable;
    }

    /**
     * Rejects points behind the camera. projectPointToScreen() can return a
     * mirrored NDC position for a point with negative view-space W, which used
     * to produce an extra square on the opposite side of the screen.
     */
    private static boolean isInFront(CameraRenderState camera, Vec3 point) {
        if (camera.orientation == null) {
            return true;
        }
        Vec3 toPoint = point.subtract(camera.pos);
        Vector3f forward = camera.orientation.transform(new Vector3f(0.0f, 0.0f, -1.0f));
        return toPoint.x * forward.x + toPoint.y * forward.y + toPoint.z * forward.z > 0.01;
    }

    private static boolean isInFrustum(CameraRenderState camera, TargetManager.Target target) {
        if (camera.cullFrustum == null) {
            return true;
        }
        for (AABB box : target.boxes) {
            if (camera.cullFrustum.isVisible(box)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPointInFrustum(CameraRenderState camera, Vec3 point) {
        return camera.cullFrustum == null
                || camera.cullFrustum.pointInFrustum(point.x, point.y, point.z);
    }

    private record SpecialSquare(Vec3 point, float halfSize) {
    }

    private static List<SpecialSquare> specialMarkers(Minecraft minecraft, CameraRenderState camera) {
        BlackoutConfig config = BlackoutMarkerMod.config();
        List<SpecialSquare> points = new ArrayList<>(config.directionMarkers.size() + 2);

        for (BlackoutConfig.DirectionMarker marker : config.directionMarkers) {
            double yaw = Math.toRadians(marker.yaw);
            Vec3 direction = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            points.add(new SpecialSquare(camera.pos.add(direction.scale(100.0)),
                    specialHalf(marker.style, config)));
        }

        if (config.sunMarkerEnabled || config.moonMarkerEnabled) {
            SkyRenderState sky = minecraft.gameRenderer.gameRenderState().levelRenderState.skyRenderState;
            if (sky != null) {
                if (config.sunMarkerEnabled) {
                    addCelestial(points, camera, sky.sunAngle, 100.0, config.sunStyle, config);
                }
                if (config.moonMarkerEnabled) {
                    addCelestial(points, camera, sky.moonAngle, 100.0, config.moonStyle, config);
                }
            }
        }
        return points;
    }

    private static float specialHalf(BlackoutConfig.Style style, BlackoutConfig config) {
        if (style != null && style.squareSize != null) {
            return style.squareSize / 2.0f;
        }
        return config.squareHalfSize;
    }

    private static void addCelestial(List<SpecialSquare> points,
                                     CameraRenderState camera,
                                     float angle,
                                     double distance,
                                     BlackoutConfig.Style style,
                                     BlackoutConfig config) {
        if (!Float.isFinite(angle)) {
            return;
        }
        Vec3 direction = new Vec3(-Math.sin(angle), Math.cos(angle), 0.0);
        if (direction.y <= -0.05) {
            return;
        }
        points.add(new SpecialSquare(camera.pos.add(direction.scale(distance)), specialHalf(style, config)));
    }

    /**
     * Approximates "any part of the hitbox/shape is visible" with rays to the
     * center and the eight inset corners of each shape box. This is deliberately
     * CPU-side: the world depth texture is not reliably available after the
     * post-processing pass on every pipeline, which previously made occluded
     * targets appear visible.
     */
    private static boolean isAnyPartVisible(Minecraft minecraft,
                                            TargetManager.Target target,
                                            CameraRenderState camera) {
        if (isPointVisible(minecraft, target, camera, target.center)) {
            return true;
        }

        for (AABB box : target.boxes) {
            double minX = box.minX + 0.01;
            double maxX = box.maxX - 0.01;
            double minY = box.minY + 0.01;
            double maxY = box.maxY - 0.01;
            double minZ = box.minZ + 0.01;
            double maxZ = box.maxZ - 0.01;

            for (int ix = 0; ix < 2; ix++) {
                for (int iy = 0; iy < 2; iy++) {
                    for (int iz = 0; iz < 2; iz++) {
                        Vec3 point = new Vec3(
                                ix == 0 ? minX : maxX,
                                iy == 0 ? minY : maxY,
                                iz == 0 ? minZ : maxZ
                        );
                        if (isPointVisible(minecraft, target, camera, point)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isPointVisible(Minecraft minecraft,
                                          TargetManager.Target target,
                                          CameraRenderState camera,
                                          Vec3 point) {
        BlockHitResult hit = minecraft.level.clip(new ClipContext(
                camera.pos,
                point,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                minecraft.player
        ));
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return true;
        }

        if (target.block) {
            return hit.getBlockPos().equals(target.blockPos);
        }

        return hit.getLocation().distanceToSqr(camera.pos)
                >= point.distanceToSqr(camera.pos) - 0.05;
    }

    private static void readCompletedQueries() {
        if (pendingQueries.isEmpty()) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer result = stack.mallocInt(1);
            Iterator<Map.Entry<String, PendingQuery>> iterator = pendingQueries.entrySet().iterator();
            while (iterator.hasNext()) {
                PendingQuery query = iterator.next().getValue();

                // Deliberately read synchronously. The query was submitted on
                // the previous frame and is normally ready by now; reading it
                // unconditionally guarantees that an occluded target cannot
                // keep a stale "visible" cache entry if the driver reports the
                // availability flag oddly.
                result.clear();
                GL33C.glGetQueryObjectiv(query.id, GL33C.GL_QUERY_RESULT, result);
                visibleCache.put(query.key, result.get(0) != 0);
                GL33C.glDeleteQueries(query.id);
                iterator.remove();
            }
        }
    }

    private static void issueQueries(List<TargetManager.Target> targets,
                                     CameraRenderState camera,
                                     int depthTexture,
                                     int width,
                                     int height) {
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, queryFbo);
        GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_DEPTH_ATTACHMENT,
                GL33C.GL_TEXTURE_2D, depthTexture, 0);
        GL33C.glDrawBuffer(GL33C.GL_NONE);
        GL33C.glReadBuffer(GL33C.GL_NONE);

        int status = GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER);
        if (status != GL33C.GL_FRAMEBUFFER_COMPLETE) {
            failOnce("query framebuffer incomplete: 0x" + Integer.toHexString(status), null);
            return;
        }

        GL33C.glViewport(0, 0, width, height);
        GL33C.glDisable(GL33C.GL_BLEND);
        GL33C.glDisable(GL33C.GL_CULL_FACE);
        GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
        GL33C.glEnable(GL33C.GL_DEPTH_TEST);
        GL33C.glDepthMask(false);
        GL33C.glDepthFunc(GL33C.GL_GEQUAL); // Minecraft 26.3 uses reverse-Z
        GL33C.glColorMask(false, false, false, false);

        GL33C.glUseProgram(cubeProgram);
        GL33C.glBindVertexArray(cubeVao);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            camera.projectionMatrix.get(matrix);
            GL33C.glUniformMatrix4fv(uProj, false, matrix);
            camera.viewRotationMatrix.get(matrix);
            GL33C.glUniformMatrix4fv(uView, false, matrix);
            GL33C.glUniform3f(uCam, (float) camera.pos.x, (float) camera.pos.y, (float) camera.pos.z);

            for (TargetManager.Target target : targets) {
                if (!target.hideWhenOccluded || pendingQueries.containsKey(target.key)) {
                    continue;
                }

                int query = GL33C.glGenQueries();
                GL33C.glBeginQuery(GL33C.GL_ANY_SAMPLES_PASSED, query);
                for (AABB rawBox : target.boxes) {
                    AABB box = rawBox.inflate(0.02);
                    GL33C.glUniform3f(uCenter,
                            (float) ((box.minX + box.maxX) * 0.5),
                            (float) ((box.minY + box.maxY) * 0.5),
                            (float) ((box.minZ + box.maxZ) * 0.5));
                    GL33C.glUniform3f(uHalf,
                            (float) ((box.maxX - box.minX) * 0.5),
                            (float) ((box.maxY - box.minY) * 0.5),
                            (float) ((box.maxZ - box.minZ) * 0.5));
                    GL33C.glDrawElements(GL33C.GL_TRIANGLES, 36, GL33C.GL_UNSIGNED_SHORT, 0L);
                }
                GL33C.glEndQuery(GL33C.GL_ANY_SAMPLES_PASSED);
                pendingQueries.put(target.key, new PendingQuery(query, target.key));
            }
        }
    }

    private static void drawSquares(Minecraft minecraft,
                                    List<TargetManager.Target> targets,
                                    CameraRenderState camera,
                                    int colorTexture,
                                    int width,
                                    int height) {
        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, overlayFbo);
        GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0,
                GL33C.GL_TEXTURE_2D, colorTexture, 0);
        GL33C.glDrawBuffer(GL33C.GL_COLOR_ATTACHMENT0);
        GL33C.glReadBuffer(GL33C.GL_NONE);

        int status = GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER);
        if (status != GL33C.GL_FRAMEBUFFER_COMPLETE) {
            failOnce("overlay framebuffer incomplete: 0x" + Integer.toHexString(status), null);
            return;
        }

        List<SpecialSquare> specials = specialMarkers(minecraft, camera);
        float[] vertices = new float[(targets.size() + specials.size()) * 12];
        int count = 0;
        for (TargetManager.Target target : targets) {
            if (!isInFrustum(camera, target) || !isInFront(camera, target.center)) {
                continue;
            }
            if (target.hideWhenOccluded) {
                if (!target.visible) {
                    continue;
                }
                if (target.centerMode && !isCenterVisible(minecraft, target, camera)) {
                    continue;
                }
            }

            Vec3 ndc = minecraft.gameRenderer.projectPointToScreen(target.center);
            if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y) || !Double.isFinite(ndc.z)) {
                continue;
            }
            if (ndc.z < -1.2 || ndc.z > 1.2 || Math.abs(ndc.x) > 2.0 || Math.abs(ndc.y) > 2.0) {
                continue;
            }

            float cx = (float) ((ndc.x * 0.5 + 0.5) * width);
            float cy = (float) ((ndc.y * 0.5 + 0.5) * height);
            float half = target.halfSize;
            int i = count * 12;
            // Two triangles, bottom-left origin.
            vertices[i++] = cx - half;
            vertices[i++] = cy - half;
            vertices[i++] = cx + half;
            vertices[i++] = cy - half;
            vertices[i++] = cx + half;
            vertices[i++] = cy + half;
            vertices[i++] = cx - half;
            vertices[i++] = cy - half;
            vertices[i++] = cx + half;
            vertices[i++] = cy + half;
            vertices[i++] = cx - half;
            vertices[i++] = cy + half;
            count++;
        }

        for (SpecialSquare special : specials) {
            Vec3 point = special.point();
            float specialHalf = special.halfSize();
            if (!isPointInFrustum(camera, point) || !isInFront(camera, point)) {
                continue;
            }
            Vec3 ndc = minecraft.gameRenderer.projectPointToScreen(point);
            if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y) || !Double.isFinite(ndc.z)) {
                continue;
            }
            if (ndc.z < -1.2 || ndc.z > 1.2 || Math.abs(ndc.x) > 2.0 || Math.abs(ndc.y) > 2.0) {
                continue;
            }

            float cx = (float) ((ndc.x * 0.5 + 0.5) * width);
            float cy = (float) ((ndc.y * 0.5 + 0.5) * height);
            int i = count * 12;
            vertices[i++] = cx - specialHalf;
            vertices[i++] = cy - specialHalf;
            vertices[i++] = cx + specialHalf;
            vertices[i++] = cy - specialHalf;
            vertices[i++] = cx + specialHalf;
            vertices[i++] = cy + specialHalf;
            vertices[i++] = cx - specialHalf;
            vertices[i++] = cy - specialHalf;
            vertices[i++] = cx + specialHalf;
            vertices[i++] = cy + specialHalf;
            vertices[i++] = cx - specialHalf;
            vertices[i++] = cy + specialHalf;
            count++;
        }

        if (count == 0) {
            return;
        }

        GL33C.glViewport(0, 0, width, height);
        GL33C.glDisable(GL33C.GL_DEPTH_TEST);
        GL33C.glDisable(GL33C.GL_BLEND);
        GL33C.glDisable(GL33C.GL_CULL_FACE);
        GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
        GL33C.glDepthMask(false);
        GL33C.glColorMask(true, true, true, true);

        GL33C.glUseProgram(squareProgram);
        GL33C.glBindVertexArray(squareVao);
        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, squareVbo);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(count * 12);
            buffer.put(vertices, 0, count * 12).flip();
            GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, buffer, GL33C.GL_STREAM_DRAW);
            GL33C.glUniform2f(uViewport, width, height);
            GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, count * 6);
        }
    }

    /**
     * Checks the stricter "center visible" mode. The target already passed the
     * any-part depth query, so this only needs to reject centers that are off
     * screen or behind world blocks. For a block target, a ray that reaches the
     * target block itself counts as visible; for an entity, the center must be
     * in front of the first block hit by the ray.
     */
    private static boolean isCenterVisible(Minecraft minecraft,
                                           TargetManager.Target target,
                                           CameraRenderState camera) {
        Vec3 ndc = minecraft.gameRenderer.projectPointToScreen(target.center);
        if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y) || !Double.isFinite(ndc.z)) {
            return false;
        }
        if (ndc.z < -1.0 || ndc.z > 1.0 || Math.abs(ndc.x) > 1.0 || Math.abs(ndc.y) > 1.0) {
            return false;
        }

        BlockHitResult hit = minecraft.level.clip(new ClipContext(
                camera.pos,
                target.center,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                minecraft.player
        ));
        if (hit == null) {
            return true;
        }

        if (target.block) {
            return hit.getBlockPos().equals(target.blockPos);
        }

        double hitDistanceSq = hit.getLocation().distanceToSqr(camera.pos);
        double centerDistanceSq = target.center.distanceToSqr(camera.pos);
        return hitDistanceSq >= centerDistanceSq - 0.05;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        queryFbo = GL33C.glGenFramebuffers();
        overlayFbo = GL33C.glGenFramebuffers();

        cubeProgram = createProgram(
                """
                #version 330 core
                layout(location = 0) in vec3 aPos;
                uniform mat4 uProj;
                uniform mat4 uView;
                uniform vec3 uCam;
                uniform vec3 uCenter;
                uniform vec3 uHalf;
                void main() {
                    vec3 world = uCenter + aPos * uHalf;
                    gl_Position = uProj * (uView * vec4(world - uCam, 1.0));
                }
                """,
                """
                #version 330 core
                layout(location = 0) out vec4 fragColor;
                void main() {
                    fragColor = vec4(1.0);
                }
                """
        );
        uProj = GL33C.glGetUniformLocation(cubeProgram, "uProj");
        uView = GL33C.glGetUniformLocation(cubeProgram, "uView");
        uCam = GL33C.glGetUniformLocation(cubeProgram, "uCam");
        uCenter = GL33C.glGetUniformLocation(cubeProgram, "uCenter");
        uHalf = GL33C.glGetUniformLocation(cubeProgram, "uHalf");

        squareProgram = createProgram(
                """
                #version 330 core
                layout(location = 0) in vec2 aPos;
                uniform vec2 uViewport;
                void main() {
                    vec2 ndc = aPos / uViewport * 2.0 - 1.0;
                    gl_Position = vec4(ndc, 0.0, 1.0);
                }
                """,
                """
                #version 330 core
                layout(location = 0) out vec4 fragColor;
                void main() {
                    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                }
                """
        );
        uViewport = GL33C.glGetUniformLocation(squareProgram, "uViewport");

        cubeVao = GL33C.glGenVertexArrays();
        cubeVbo = GL33C.glGenBuffers();
        cubeEbo = GL33C.glGenBuffers();
        GL33C.glBindVertexArray(cubeVao);
        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, cubeVbo);
        GL33C.glBufferData(GL33C.GL_ARRAY_BUFFER, cubeVertices(), GL33C.GL_STATIC_DRAW);
        GL33C.glBindBuffer(GL33C.GL_ELEMENT_ARRAY_BUFFER, cubeEbo);
        GL33C.glBufferData(GL33C.GL_ELEMENT_ARRAY_BUFFER, cubeIndices(), GL33C.GL_STATIC_DRAW);
        GL33C.glEnableVertexAttribArray(0);
        GL33C.glVertexAttribPointer(0, 3, GL33C.GL_FLOAT, false, 0, 0L);

        squareVao = GL33C.glGenVertexArrays();
        squareVbo = GL33C.glGenBuffers();
        GL33C.glBindVertexArray(squareVao);
        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, squareVbo);
        GL33C.glEnableVertexAttribArray(0);
        GL33C.glVertexAttribPointer(0, 2, GL33C.GL_FLOAT, false, 0, 0L);

        GL33C.glBindVertexArray(0);
        GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, 0);
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = createShader(GL33C.GL_VERTEX_SHADER, vertexSource);
        int fragment = createShader(GL33C.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL33C.glCreateProgram();
        GL33C.glAttachShader(program, vertex);
        GL33C.glAttachShader(program, fragment);
        GL33C.glLinkProgram(program);
        GL33C.glDeleteShader(vertex);
        GL33C.glDeleteShader(fragment);
        if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == GL33C.GL_FALSE) {
            throw new IllegalStateException("shader link failed: " + GL33C.glGetProgramInfoLog(program));
        }
        return program;
    }

    private static int createShader(int type, String source) {
        int shader = GL33C.glCreateShader(type);
        GL33C.glShaderSource(shader, source);
        GL33C.glCompileShader(shader);
        if (GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == GL33C.GL_FALSE) {
            throw new IllegalStateException("shader compile failed: " + GL33C.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private static FloatBuffer cubeVertices() {
        float[] data = {
                -1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1,
                -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1
        };
        return org.lwjgl.BufferUtils.createFloatBuffer(data.length).put(data).flip();
    }

    private static IntBuffer cubeIndices() {
        int[] data = {
                0, 1, 2, 0, 2, 3,
                4, 6, 5, 4, 7, 6,
                0, 4, 5, 0, 5, 1,
                3, 2, 6, 3, 6, 7,
                0, 3, 7, 0, 7, 4,
                1, 5, 6, 1, 6, 2
        };
        return org.lwjgl.BufferUtils.createIntBuffer(data.length).put(data).flip();
    }

    private static void failOnce(String message, Throwable throwable) {
        broken = true;
        if (!loggedFailure) {
            loggedFailure = true;
            if (throwable == null) {
                BlackoutMarkerMod.LOGGER.error(message);
            } else {
                BlackoutMarkerMod.LOGGER.error(message, throwable);
            }
        }
    }

    private static final class PendingQuery {
        private final int id;
        private final String key;

        private PendingQuery(int id, String key) {
            this.id = id;
            this.key = key;
        }
    }

    private static final class GlState {
        private final int fbo;
        private final int drawFbo;
        private final int readFbo;
        private final int program;
        private final int vao;
        private final int arrayBuffer;
        private final int depthFunc;
        private final int[] viewport = new int[4];
        private final boolean[] colorMask = new boolean[4];
        private final boolean depthTest;
        private final boolean blend;
        private final boolean cull;
        private final boolean scissor;
        private final boolean depthMask;

        private GlState() {
            fbo = GL33C.glGetInteger(GL33C.GL_FRAMEBUFFER_BINDING);
            drawFbo = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
            readFbo = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
            program = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
            vao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING);
            arrayBuffer = GL33C.glGetInteger(GL33C.GL_ARRAY_BUFFER_BINDING);
            depthFunc = GL33C.glGetInteger(GL33C.GL_DEPTH_FUNC);
            GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, viewport);
            depthTest = GL33C.glIsEnabled(GL33C.GL_DEPTH_TEST);
            blend = GL33C.glIsEnabled(GL33C.GL_BLEND);
            cull = GL33C.glIsEnabled(GL33C.GL_CULL_FACE);
            scissor = GL33C.glIsEnabled(GL33C.GL_SCISSOR_TEST);
            depthMask = GL33C.glGetBoolean(GL33C.GL_DEPTH_WRITEMASK);
            java.nio.ByteBuffer mask = org.lwjgl.BufferUtils.createByteBuffer(4);
            GL33C.glGetBooleanv(GL33C.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) {
                colorMask[i] = mask.get(i) != 0;
            }
        }

        private void restore() {
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, readFbo);
            GL33C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL33C.glUseProgram(program);
            GL33C.glBindVertexArray(vao);
            GL33C.glBindBuffer(GL33C.GL_ARRAY_BUFFER, arrayBuffer);
            GL33C.glDepthFunc(depthFunc);
            GL33C.glDepthMask(depthMask);
            GL33C.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            set(GL33C.GL_DEPTH_TEST, depthTest);
            set(GL33C.GL_BLEND, blend);
            set(GL33C.GL_CULL_FACE, cull);
            set(GL33C.GL_SCISSOR_TEST, scissor);
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, fbo);
        }

        private static void set(int cap, boolean enabled) {
            if (enabled) {
                GL33C.glEnable(cap);
            } else {
                GL33C.glDisable(cap);
            }
        }
    }
}
