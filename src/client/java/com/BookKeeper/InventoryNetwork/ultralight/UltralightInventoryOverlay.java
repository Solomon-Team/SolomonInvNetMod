package com.BookKeeper.InventoryNetwork.ultralight;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Ultralight-based inventory overlay that replaces the chest sync interface.
 * Renders HTML/CSS UI using the Ultralight engine.
 */
public class UltralightInventoryOverlay {
    private static boolean visible = false;
    private static boolean initialized = false;
    private static boolean shaderInitialized = false;

    private static int lastMouseX = 0;
    private static int lastMouseY = 0;

    // Minecraft texture system integration
    private static DynamicTexture dynamicTexture = null;
    private static ResourceLocation textureLocation = null;
    private static NativeImage nativeImage = null;
    private static boolean textureRegistered = false;

    // OpenGL resources for custom shader rendering
    private static int shaderProgram = 0;
    private static int vao = 0;
    private static int vbo = 0;
    private static int ebo = 0;

    // Shader source code
    private static final String VERTEX_SHADER =
        "#version 330 core\n" +
        "layout (location = 0) in vec2 aPos;\n" +
        "layout (location = 1) in vec2 aTexCoord;\n" +
        "out vec2 TexCoord;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(aPos.x, aPos.y, 0.0, 1.0);\n" +
        "    TexCoord = aTexCoord;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "#version 330 core\n" +
        "out vec4 FragColor;\n" +
        "in vec2 TexCoord;\n" +
        "uniform sampler2D uiTexture;\n" +
        "void main() {\n" +
        "    FragColor = texture(uiTexture, TexCoord);\n" +
        "}\n";

    // Quad vertices: position (x, y) + texture coords (u, v)
    // Covers the entire screen in normalized device coordinates (-1 to 1)
    // Texture coords flipped vertically because Ultralight renders top-down
    private static final float[] QUAD_VERTICES = {
        // positions    // texture coords (V flipped)
        -1.0f,  1.0f,   0.0f, 1.0f,  // top left     -> bottom of texture
         1.0f,  1.0f,   1.0f, 1.0f,  // top right    -> bottom of texture
         1.0f, -1.0f,   1.0f, 0.0f,  // bottom right -> top of texture
        -1.0f, -1.0f,   0.0f, 0.0f   // bottom left  -> top of texture
    };

    private static final int[] QUAD_INDICES = {
        0, 1, 2,
        2, 3, 0
    };

    /**
     * Toggle overlay visibility.
     */
    public static void toggle() {
        visible = !visible;

        if (visible) {
            if (!initialized) {
                initialize();
            }
            // Skip JavaScript notification for now - ulViewEvaluateScript has issues
            // UltralightManager.getInstance().executeScript("if(typeof onOverlayOpen==='function')onOverlayOpen();");
        } else {
            // Skip JavaScript notification for now
            // UltralightManager.getInstance().executeScript("if(typeof onOverlayClose==='function')onOverlayClose();");
        }
    }

    /**
     * Initialize Ultralight and load the inventory HTML.
     */
    private static void initialize() {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        UltralightManager manager = UltralightManager.getInstance();

        if (manager.initialize(width, height)) {
            // Load VERY simple HTML first to test if rendering works at all
            String simpleHtml = """
                <html>
                <head>
                <style>
                body { background: red; }
                h1 { color: white; font-size: 72px; text-align: center; margin-top: 100px; }
                </style>
                </head>
                <body>
                <h1>ULTRALIGHT WORKS!</h1>
                </body>
                </html>
                """;

            // Load inline HTML with embedded CSS - Sidebar panel layout
            // Optimized for vertical panel on right side of screen
            String testHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: 'Segoe UI', Arial, sans-serif;
                    background: transparent;
                    color: #e0f0f0;
                    padding: 8px;
                }
                .header {
                    background: rgba(26, 37, 82, 0.95);
                    border: 1px solid #f8d038;
                    border-radius: 8px;
                    padding: 10px 12px;
                    margin-bottom: 10px;
                }
                .logo {
                    font-size: 16px;
                    font-weight: bold;
                    color: #f8d038;
                    margin-bottom: 8px;
                    display: block;
                }
                .search {
                    width: 100%;
                    padding: 8px 10px;
                    border: 1px solid #35c6c6;
                    border-radius: 6px;
                    background: rgba(18, 26, 58, 0.95);
                    color: #e0f0f0;
                    font-size: 12px;
                }
                .grid {
                    display: grid;
                    grid-template-columns: repeat(4, 1fr);
                    gap: 6px;
                }
                .item {
                    aspect-ratio: 1;
                    background: rgba(26, 37, 82, 0.95);
                    border: 1px solid rgba(255,255,255,0.2);
                    border-radius: 4px;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    position: relative;
                    padding: 3px;
                }
                .item-name {
                    font-size: 8px;
                    color: #e0f0f0;
                    text-align: center;
                }
                .item-icon {
                    width: 24px;
                    height: 24px;
                    background: rgba(53, 198, 198, 0.4);
                    border-radius: 3px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-weight: bold;
                    color: #35c6c6;
                    font-size: 11px;
                    margin-bottom: 2px;
                }
                .item-icon.diamond { background: rgba(0, 200, 255, 0.4); color: #00c8ff; }
                .item-icon.emerald { background: rgba(0, 200, 100, 0.4); color: #00c864; }
                .item-icon.gold { background: rgba(255, 200, 0, 0.4); color: #ffc800; }
                .item-icon.iron { background: rgba(200, 200, 200, 0.4); color: #c8c8c8; }
                .item-icon.netherite { background: rgba(80, 60, 70, 0.6); color: #a08090; }
                .item-icon.redstone { background: rgba(255, 50, 50, 0.4); color: #ff3232; }
                .item-qty {
                    position: absolute;
                    bottom: 2px;
                    right: 2px;
                    background: rgba(0,0,0,0.85);
                    color: #f8d038;
                    padding: 1px 3px;
                    border-radius: 2px;
                    font-size: 8px;
                    font-weight: bold;
                }
                .item-price {
                    position: absolute;
                    top: 2px;
                    right: 2px;
                    width: 5px;
                    height: 5px;
                    border-radius: 50%;
                    background: #3bb273;
                }
                .item-price.down { background: #e65a5a; }
                .item.low-stock { border-color: #e65a5a; }
                .stats {
                    margin-top: 10px;
                    display: grid;
                    grid-template-columns: repeat(2, 1fr);
                    gap: 6px;
                }
                .stat-card {
                    background: rgba(26, 37, 82, 0.95);
                    border: 1px solid #35c6c6;
                    border-radius: 6px;
                    padding: 8px;
                    text-align: center;
                }
                .stat-value {
                    font-size: 14px;
                    font-weight: bold;
                    color: #f8d038;
                }
                .stat-label {
                    font-size: 9px;
                    color: #9fb0d0;
                }
                </style>
                </head>
                <body>
                <div class="header">
                    <span class="logo">Inventory Network</span>
                    <input type="text" class="search" placeholder="Search...">
                </div>
                <div class="grid">
                    <div class="item"><div class="item-icon diamond">D</div><span class="item-name">Diamond</span><span class="item-qty">1.2k</span><span class="item-price"></span></div>
                    <div class="item"><div class="item-icon emerald">E</div><span class="item-name">Emerald</span><span class="item-qty">3.2k</span><span class="item-price down"></span></div>
                    <div class="item low-stock"><div class="item-icon netherite">N</div><span class="item-name">Netherite</span><span class="item-qty">89</span><span class="item-price"></span></div>
                    <div class="item"><div class="item-icon gold">G</div><span class="item-name">Gold</span><span class="item-qty">15k</span><span class="item-price"></span></div>
                    <div class="item"><div class="item-icon iron">I</div><span class="item-name">Iron</span><span class="item-qty">8.7k</span><span class="item-price down"></span></div>
                    <div class="item"><div class="item-icon diamond">L</div><span class="item-name">Lapis</span><span class="item-qty">2.1k</span><span class="item-price"></span></div>
                    <div class="item"><div class="item-icon gold">C</div><span class="item-name">Copper</span><span class="item-qty">640</span><span class="item-price"></span></div>
                    <div class="item low-stock"><div class="item-icon redstone">R</div><span class="item-name">Redstone</span><span class="item-qty">45</span><span class="item-price down"></span></div>
                    <div class="item"><div class="item-icon">Q</div><span class="item-name">Quartz</span><span class="item-qty">320</span><span class="item-price"></span></div>
                    <div class="item"><div class="item-icon">Wo</div><span class="item-name">Wood</span><span class="item-qty">5.6k</span><span class="item-price"></span></div>
                    <div class="item"><div class="item-icon iron">St</div><span class="item-name">Stone</span><span class="item-qty">42k</span><span class="item-price down"></span></div>
                    <div class="item"><div class="item-icon emerald">Sl</div><span class="item-name">Slime</span><span class="item-qty">28k</span><span class="item-price"></span></div>
                </div>
                <div class="stats">
                    <div class="stat-card"><div class="stat-value">124k</div><div class="stat-label">Items</div></div>
                    <div class="stat-card"><div class="stat-value">12</div><div class="stat-label">Types</div></div>
                    <div class="stat-card"><div class="stat-value">2</div><div class="stat-label">Low</div></div>
                    <div class="stat-card"><div class="stat-value">2.4M</div><div class="stat-label">Value</div></div>
                </div>
                </body>
                </html>
                """;
            // Load the nice inventory grid UI
            manager.loadHTMLString(testHtml);
            System.out.println("[UltralightOverlay] Inventory UI HTML loaded");
            initialized = true;
            System.out.println("[UltralightInventoryOverlay] Initialized at " + width + "x" + height);
        } else {
            System.err.println("[UltralightInventoryOverlay] Failed to initialize UltralightManager");
        }
    }

    /**
     * Initialize the custom shader program for rendering Ultralight texture.
     */
    private static void initShader() {
        if (shaderInitialized) return;

        try {
            // Compile vertex shader
            int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vertexShader, VERTEX_SHADER);
            GL20.glCompileShader(vertexShader);
            if (GL20.glGetShaderi(vertexShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[UltralightOverlay] Vertex shader error: " + GL20.glGetShaderInfoLog(vertexShader));
                return;
            }

            // Compile fragment shader
            int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fragmentShader, FRAGMENT_SHADER);
            GL20.glCompileShader(fragmentShader);
            if (GL20.glGetShaderi(fragmentShader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[UltralightOverlay] Fragment shader error: " + GL20.glGetShaderInfoLog(fragmentShader));
                return;
            }

            // Link shader program
            shaderProgram = GL20.glCreateProgram();
            GL20.glAttachShader(shaderProgram, vertexShader);
            GL20.glAttachShader(shaderProgram, fragmentShader);
            GL20.glLinkProgram(shaderProgram);
            if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                System.err.println("[UltralightOverlay] Shader link error: " + GL20.glGetProgramInfoLog(shaderProgram));
                return;
            }

            // Clean up shaders (no longer needed after linking)
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);

            // Create VAO
            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);

            // Create VBO
            vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            FloatBuffer vertexBuffer = MemoryUtil.memAllocFloat(QUAD_VERTICES.length);
            vertexBuffer.put(QUAD_VERTICES).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(vertexBuffer);

            // Create EBO
            ebo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            IntBuffer indexBuffer = MemoryUtil.memAllocInt(QUAD_INDICES.length);
            indexBuffer.put(QUAD_INDICES).flip();
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(indexBuffer);

            // Set up vertex attributes
            // Position attribute (location 0)
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);
            GL20.glEnableVertexAttribArray(0);
            // Texture coord attribute (location 1)
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);

            // Unbind
            GL30.glBindVertexArray(0);

            shaderInitialized = true;
            System.out.println("[UltralightOverlay] Custom shader initialized successfully");

        } catch (Exception e) {
            System.err.println("[UltralightOverlay] Shader init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Frame counter for debug
    private static int frameCount = 0;
    private static boolean useTextureRendering = false; // Start with blocks since texture has issues

    // Panel dimensions and position (for inventory sidebar mode)
    private static final int PANEL_WIDTH = 300; // Width of the panel in pixels
    private static final float PANEL_WIDTH_RATIO = 0.35f; // Panel takes 35% of screen width

    /**
     * Render the overlay. Called from HUD render callback.
     */
    public static void render(GuiGraphics graphics) {
        if (!visible || !initialized) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Calculate panel dimensions (right side of screen)
        int panelWidth = (int)(screenWidth * PANEL_WIDTH_RATIO);
        int panelX = screenWidth - panelWidth;
        int panelY = 0;
        int panelHeight = screenHeight;

        // Update and render Ultralight to its internal texture
        UltralightManager manager = UltralightManager.getInstance();
        manager.update();
        manager.render();

        frameCount++;

        // Draw semi-transparent background for the panel
        graphics.fill(panelX, panelY, screenWidth, panelHeight, 0xDD121a3a);

        // Try to render using OpenGL texture (fast and smooth)
        if (useTextureRendering) {
            renderWithTextureToPanel(graphics, manager, panelX, panelY, panelWidth, panelHeight);
        } else {
            // Fallback: render as blocks
            manager.copyPixelData();
            renderUltralightAsBlocksToPanel(graphics, manager, panelX, panelY, panelWidth, panelHeight);
        }

        // Panel border
        graphics.fill(panelX, panelY, panelX + 2, panelHeight, 0xFFf8d038); // Left gold border

        // Debug info at top of panel
        String mode = useTextureRendering ? "Texture" : "Blocks";
        graphics.drawString(mc.font, "Mode: " + mode + " [B]", panelX + 8, 8, 0xFFFFFF00, true);

        // Instructions at bottom of panel
        graphics.drawString(mc.font, "Press I to close", panelX + 8, screenHeight - 16, 0xFFAAAAAA, true);
    }

    /**
     * Render Ultralight texture to a specific panel area using texture rendering.
     */
    private static void renderWithTextureToPanel(GuiGraphics graphics, UltralightManager manager,
                                                  int panelX, int panelY, int panelWidth, int panelHeight) {
        int textureId = manager.getTexture();
        if (textureId == 0) {
            return;
        }

        // Initialize shader if needed
        if (!shaderInitialized) {
            initShader();
            if (!shaderInitialized) {
                useTextureRendering = false;
                manager.copyPixelData();
                renderUltralightAsBlocksToPanel(graphics, manager, panelX, panelY, panelWidth, panelHeight);
                return;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        int windowWidth = mc.getWindow().getWidth();
        int windowHeight = mc.getWindow().getHeight();
        double guiScale = mc.getWindow().getGuiScale();

        // Convert GUI coordinates to screen coordinates
        int screenPanelX = (int)(panelX * guiScale);
        int screenPanelY = (int)(panelY * guiScale);
        int screenPanelWidth = (int)(panelWidth * guiScale);
        int screenPanelHeight = (int)(panelHeight * guiScale);

        // Save OpenGL state
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        // Set up scissor to only render in panel area
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(screenPanelX, windowHeight - screenPanelY - screenPanelHeight, screenPanelWidth, screenPanelHeight);

        // Set up state for overlay rendering
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // Use our shader
        GL20.glUseProgram(shaderProgram);

        // Bind our texture
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        int texLoc = GL20.glGetUniformLocation(shaderProgram, "uiTexture");
        GL20.glUniform1i(texLoc, 0);

        // Draw the quad
        GL30.glBindVertexArray(vao);
        GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0);

        // Restore OpenGL state
        GL30.glBindVertexArray(previousVao);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        GL20.glUseProgram(previousProgram);

        if (!scissorWasEnabled) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (!blendWasEnabled) GL11.glDisable(GL11.GL_BLEND);
        if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /**
     * Render Ultralight content as blocks to a specific panel area.
     */
    private static void renderUltralightAsBlocksToPanel(GuiGraphics graphics, UltralightManager manager,
                                                         int panelX, int panelY, int panelWidth, int panelHeight) {
        java.nio.ByteBuffer pixels = manager.getPixelBuffer();
        if (pixels == null || pixels.remaining() == 0) {
            return;
        }

        int ulWidth = manager.getBitmapWidth();
        int ulHeight = manager.getBitmapHeight();
        int rowBytes = manager.getRowBytes();

        if (ulWidth == 0 || ulHeight == 0) {
            return;
        }

        // Calculate scaling from Ultralight bitmap to panel coordinates
        float scaleX = (float) panelWidth / ulWidth;
        float scaleY = (float) panelHeight / ulHeight;

        // Use large blocks for fast performance (trading quality for FPS)
        // 16px blocks = ~50x50 draw calls for a 800x800 panel = very fast
        int blockSize = 16;
        int drawSizeX = Math.max(1, (int) Math.ceil(blockSize * scaleX));
        int drawSizeY = Math.max(1, (int) Math.ceil(blockSize * scaleY));

        // Sample and draw pixels
        for (int y = 0; y < ulHeight; y += blockSize) {
            int drawY = panelY + (int)(y * scaleY);
            if (drawY >= panelY + panelHeight) break;

            for (int x = 0; x < ulWidth; x += blockSize) {
                int srcOffset = y * rowBytes + x * 4;
                if (srcOffset + 3 < pixels.limit()) {
                    int b = pixels.get(srcOffset) & 0xFF;
                    int g = pixels.get(srcOffset + 1) & 0xFF;
                    int r = pixels.get(srcOffset + 2) & 0xFF;
                    int a = pixels.get(srcOffset + 3) & 0xFF;

                    if (a > 10) { // Skip nearly transparent pixels
                        int color = (a << 24) | (r << 16) | (g << 8) | b;
                        int drawX = panelX + (int)(x * scaleX);
                        graphics.fill(drawX, drawY, drawX + drawSizeX, drawY + drawSizeY, color);
                    }
                }
            }
        }
    }

    /**
     * Render Ultralight texture using raw OpenGL with proper state management.
     */
    private static void renderWithTexture(GuiGraphics graphics, UltralightManager manager, int screenWidth, int screenHeight) {
        int textureId = manager.getTexture();
        if (textureId == 0) {
            return;
        }

        // Initialize shader if needed
        if (!shaderInitialized) {
            initShader();
            if (!shaderInitialized) {
                // Fall back to block rendering if shader fails
                useTextureRendering = false;
                manager.copyPixelData();
                renderUltralightAsBlocks(graphics, manager, screenWidth, screenHeight);
                return;
            }
        }

        // Get actual window dimensions (not GUI scaled)
        Minecraft mc = Minecraft.getInstance();
        int windowWidth = mc.getWindow().getWidth();
        int windowHeight = mc.getWindow().getHeight();
        double guiScale = mc.getWindow().getGuiScale();

        // Save OpenGL state
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);

        // Save blend function
        int srcBlend = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstBlend = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);

        // Set up state for overlay rendering
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // Use our shader
        GL20.glUseProgram(shaderProgram);

        // Bind our texture
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        int texLoc = GL20.glGetUniformLocation(shaderProgram, "uiTexture");
        GL20.glUniform1i(texLoc, 0);

        // Draw the quad
        GL30.glBindVertexArray(vao);
        GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0);

        // Restore OpenGL state
        GL30.glBindVertexArray(previousVao);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        GL20.glUseProgram(previousProgram);

        if (!blendWasEnabled) GL11.glDisable(GL11.GL_BLEND);
        if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(srcBlend, dstBlend);
    }

    /**
     * Render Ultralight content using GuiGraphics.fill() for each block of pixels.
     * Optimized version using larger blocks for better performance.
     */
    private static void renderUltralightAsBlocks(GuiGraphics graphics, UltralightManager manager, int screenWidth, int screenHeight) {
        java.nio.ByteBuffer pixels = manager.getPixelBuffer();
        if (pixels == null || pixels.remaining() == 0) {
            graphics.fill(0, 0, screenWidth, screenHeight, 0xFFFF0000); // Red = no pixels
            return;
        }

        int ulWidth = manager.getBitmapWidth();
        int ulHeight = manager.getBitmapHeight();
        int rowBytes = manager.getRowBytes();

        if (ulWidth == 0 || ulHeight == 0) {
            graphics.fill(0, 0, screenWidth, screenHeight, 0xFF0000FF); // Blue = no dimensions
            return;
        }

        // Calculate scaling from Ultralight bitmap to GUI coordinates
        float scaleX = (float) screenWidth / ulWidth;
        float scaleY = (float) screenHeight / ulHeight;

        // Use larger blocks for better performance
        // Block size of 8 gives reasonable quality with good FPS
        int blockSize = 8;
        int drawSizeX = Math.max(1, (int) Math.ceil(blockSize * scaleX));
        int drawSizeY = Math.max(1, (int) Math.ceil(blockSize * scaleY));

        for (int y = 0; y < ulHeight; y += blockSize) {
            for (int x = 0; x < ulWidth; x += blockSize) {
                int srcOffset = y * rowBytes + x * 4;
                if (srcOffset + 3 < pixels.limit()) {
                    int b = pixels.get(srcOffset) & 0xFF;
                    int g = pixels.get(srcOffset + 1) & 0xFF;
                    int r = pixels.get(srcOffset + 2) & 0xFF;
                    int a = pixels.get(srcOffset + 3) & 0xFF;

                    if (a > 0) { // Only draw non-transparent pixels
                        int color = (a << 24) | (r << 16) | (g << 8) | b;
                        int drawX = (int)(x * scaleX);
                        int drawY = (int)(y * scaleY);
                        graphics.fill(drawX, drawY, drawX + drawSizeX, drawY + drawSizeY, color);
                    }
                }
            }
        }
    }

    // Debug counter for shader
    private static int shaderRenderCount = 0;

    /**
     * Render the Ultralight texture using the custom shader program.
     */
    private static void renderWithCustomShader(int textureId) {
        // Debug: check for GL errors before we start
        int preError = GL11.glGetError();
        if (preError != GL11.GL_NO_ERROR && shaderRenderCount < 5) {
            System.out.println("[UltralightOverlay] Pre-render GL error: " + preError);
        }

        // Save current OpenGL state
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        // Save viewport
        IntBuffer viewport = MemoryUtil.memAllocInt(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        // Get window dimensions
        Minecraft mc = Minecraft.getInstance();
        int windowWidth = mc.getWindow().getWidth();
        int windowHeight = mc.getWindow().getHeight();

        if (shaderRenderCount < 5) {
            System.out.println("[UltralightOverlay] Rendering - Window: " + windowWidth + "x" + windowHeight +
                ", Texture: " + textureId + ", Shader: " + shaderProgram + ", VAO: " + vao);
        }

        // Set viewport to full window (not scaled GUI)
        GL11.glViewport(0, 0, windowWidth, windowHeight);

        // Set up rendering state - disable EVERYTHING that could interfere
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDepthMask(false);
        GL11.glColorMask(true, true, true, true);

        // TEST: Clear screen to green to verify we have control
        if (shaderRenderCount < 10) {
            GL11.glClearColor(0.0f, 0.5f, 0.0f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        // Use our shader program
        GL20.glUseProgram(shaderProgram);

        // Bind the Ultralight texture to texture unit 0
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        int uniformLoc = GL20.glGetUniformLocation(shaderProgram, "uiTexture");
        GL20.glUniform1i(uniformLoc, 0);

        if (shaderRenderCount < 5) {
            System.out.println("[UltralightOverlay] Uniform location for uiTexture: " + uniformLoc);
        }

        // Draw the fullscreen quad
        GL30.glBindVertexArray(vao);
        GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0);

        // Check for GL errors after draw
        int postError = GL11.glGetError();
        if (postError != GL11.GL_NO_ERROR && shaderRenderCount < 5) {
            System.out.println("[UltralightOverlay] Post-draw GL error: " + postError);
        }

        shaderRenderCount++;

        // Restore viewport
        GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
        MemoryUtil.memFree(viewport);

        // Restore previous OpenGL state
        GL30.glBindVertexArray(previousVao);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        GL20.glUseProgram(previousProgram);

        if (!blendEnabled) GL11.glDisable(GL11.GL_BLEND);
        if (depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (cullEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
    }

    /**
     * Handle mouse movement.
     */
    public static void onMouseMove(double mouseX, double mouseY) {
        if (!visible || !initialized) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Convert GUI coordinates to Ultralight pixel coordinates
        double guiScale = mc.getWindow().getGuiScale();
        int ulX = (int) (mouseX * guiScale);
        int ulY = (int) (mouseY * guiScale);

        lastMouseX = ulX;
        lastMouseY = ulY;

        UltralightManager.getInstance().fireMouseMove(ulX, ulY);
    }

    /**
     * Handle mouse button press/release.
     */
    public static boolean onMouseButton(int button, int action) {
        if (!visible || !initialized) {
            return false;
        }

        boolean pressed = (action == GLFW.GLFW_PRESS);
        UltralightManager.getInstance().fireMouseButton(lastMouseX, lastMouseY, button, pressed);

        // Consume the event when overlay is visible
        return true;
    }

    /**
     * Handle mouse scroll.
     */
    public static boolean onMouseScroll(double deltaX, double deltaY) {
        if (!visible || !initialized) {
            return false;
        }

        // Scale scroll amount
        int scrollX = (int) (deltaX * 32);
        int scrollY = (int) (deltaY * 32);

        UltralightManager.getInstance().fireScroll(lastMouseX, lastMouseY, scrollX, scrollY);

        return true;
    }

    /**
     * Handle key press.
     */
    public static boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if (!visible || !initialized) {
            return false;
        }

        // ESC closes overlay
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            toggle();
            return true;
        }

        // B toggles between texture and block rendering
        if (keyCode == GLFW.GLFW_KEY_B) {
            useTextureRendering = !useTextureRendering;
            System.out.println("[UltralightOverlay] Render mode: " + (useTextureRendering ? "Texture" : "Blocks"));
            return true;
        }

        UltralightManager.getInstance().fireKeyEvent(keyCode, true);
        return true;
    }

    /**
     * Handle key release.
     */
    public static boolean onKeyRelease(int keyCode, int scanCode, int modifiers) {
        if (!visible || !initialized) {
            return false;
        }

        UltralightManager.getInstance().fireKeyEvent(keyCode, false);
        return true;
    }

    /**
     * Handle character input (for text fields).
     */
    public static boolean onCharTyped(char codePoint, int modifiers) {
        if (!visible || !initialized) {
            return false;
        }

        UltralightManager.getInstance().fireCharEvent(codePoint);
        return true;
    }

    /**
     * Called when game window resizes.
     */
    public static void onResize(int width, int height) {
        if (!initialized) {
            return;
        }

        UltralightManager.getInstance().resize(width, height);
    }

    /**
     * Clean up resources when mod unloads.
     */
    public static void shutdown() {
        if (initialized) {
            UltralightManager.getInstance().shutdown();
            initialized = false;
        }
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void setVisible(boolean visible) {
        UltralightInventoryOverlay.visible = visible;
    }

    /**
     * Toggle between texture and block rendering modes.
     */
    public static void toggleRenderMode() {
        useTextureRendering = !useTextureRendering;
        System.out.println("[UltralightOverlay] Render mode: " + (useTextureRendering ? "Texture" : "Blocks"));
    }

    /**
     * Execute JavaScript in the overlay.
     */
    public static void executeScript(String script) {
        if (initialized) {
            UltralightManager.getInstance().executeScript(script);
        }
    }

    /**
     * Update inventory data from Java to JavaScript.
     * Call this when chest data changes.
     */
    public static void updateInventoryData(String jsonData) {
        if (initialized) {
            // Escape the JSON for JavaScript string
            String escaped = jsonData.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
            executeScript("if(typeof updateFromJava==='function')updateFromJava('" + escaped + "');");
        }
    }
}
