package com.BookKeeper.InventoryNetwork.ultralight;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.ultralight.AppCore.*;
import static org.lwjgl.ultralight.Ultralight.*;

/**
 * Manages Ultralight renderer lifecycle and rendering for Minecraft overlay.
 * Uses CPU rendering (bitmap surface) for simplicity and compatibility.
 */
public class UltralightManager {
    private static UltralightManager instance;

    private long config = MemoryUtil.NULL;
    private long renderer = MemoryUtil.NULL;
    private long viewConfig = MemoryUtil.NULL;
    private long view = MemoryUtil.NULL;

    private int glTexture = 0;
    private int width;
    private int height;
    private boolean initialized = false;
    private boolean needsResize = false;

    private UltralightManager() {}

    public static UltralightManager getInstance() {
        if (instance == null) {
            instance = new UltralightManager();
        }
        return instance;
    }

    /**
     * Initialize Ultralight renderer with specified dimensions.
     */
    public boolean initialize(int width, int height) {
        if (initialized) {
            return true;
        }

        this.width = width;
        this.height = height;

        try {
            System.out.println("[UltralightManager] Initializing Ultralight " + ulVersionString());

            // Enable platform-specific handlers BEFORE creating renderer
            // This sets up file system and font loading which are required
            System.out.println("[UltralightManager] Enabling platform file system...");
            ulEnablePlatformFileSystem(ulCreateString("./"));
            System.out.println("[UltralightManager] Enabling platform font loader...");
            ulEnablePlatformFontLoader();

            // Create configuration (bare minimum - no config options to avoid JNI issues)
            System.out.println("[UltralightManager] Creating config...");
            this.config = ulCreateConfig();
            System.out.println("[UltralightManager] Config created: " + config);

            // Skip ulConfigSetResourcePathPrefix - may have JNI compatibility issues
            // Ultralight will look for resources in current directory by default

            // Create renderer
            System.out.println("[UltralightManager] Creating renderer...");
            this.renderer = ulCreateRenderer(config);
            System.out.println("[UltralightManager] Renderer created: " + renderer);
            if (renderer == MemoryUtil.NULL) {
                System.err.println("[UltralightManager] Failed to create renderer");
                return false;
            }

            // Create view configuration - use CPU rendering (not accelerated)
            System.out.println("[UltralightManager] Creating view config...");
            this.viewConfig = ulCreateViewConfig();
            System.out.println("[UltralightManager] Setting view config options...");
            ulViewConfigSetIsAccelerated(viewConfig, false);
            ulViewConfigSetIsTransparent(viewConfig, true);

            // Create view
            System.out.println("[UltralightManager] Creating view " + width + "x" + height + "...");
            this.view = ulCreateView(renderer, width, height, viewConfig, MemoryUtil.NULL);
            System.out.println("[UltralightManager] View created: " + view);
            if (view == MemoryUtil.NULL) {
                System.err.println("[UltralightManager] Failed to create view");
                return false;
            }

            // Create OpenGL texture for rendering
            this.glTexture = GL45.glCreateTextures(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexture);
            GL45.glTextureParameteri(glTexture, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL45.glTextureParameteri(glTexture, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL45.glTextureParameteri(glTexture, GL11.GL_TEXTURE_WRAP_S, GL45.GL_CLAMP_TO_EDGE);
            GL45.glTextureParameteri(glTexture, GL11.GL_TEXTURE_WRAP_T, GL45.GL_CLAMP_TO_EDGE);

            initialized = true;
            System.out.println("[UltralightManager] Initialization complete");
            return true;

        } catch (Exception e) {
            System.err.println("[UltralightManager] Initialization failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Load HTML content from a resource file.
     */
    public void loadHTML(String resourcePath) {
        if (!initialized || view == MemoryUtil.NULL) {
            System.err.println("[UltralightManager] Cannot load HTML - not initialized");
            return;
        }

        try {
            String html = loadResourceAsString(resourcePath);
            if (html != null) {
                long str = ulCreateString(html);
                ulViewLoadHTML(view, str);
                ulDestroyString(str);
                System.out.println("[UltralightManager] Loaded HTML from: " + resourcePath);
            }
        } catch (Exception e) {
            System.err.println("[UltralightManager] Failed to load HTML: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load HTML content directly from a string.
     */
    public void loadHTMLString(String html) {
        if (!initialized || view == MemoryUtil.NULL) {
            System.err.println("[UltralightManager] Cannot load HTML - not initialized");
            return;
        }

        try {
            System.out.println("[UltralightManager] Loading HTML string, length: " + html.length());
            long str = ulCreateString(html);
            System.out.println("[UltralightManager] Created ULString: " + str);
            ulViewLoadHTML(view, str);
            System.out.println("[UltralightManager] Called ulViewLoadHTML");
            ulDestroyString(str);

            // Force an update cycle to start loading
            ulUpdate(renderer);
            System.out.println("[UltralightManager] HTML load initiated");
        } catch (Exception e) {
            System.err.println("[UltralightManager] Failed to load HTML string: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update Ultralight (process events, animations, etc.)
     * Call this every frame.
     */
    public void update() {
        if (!initialized || renderer == MemoryUtil.NULL) {
            return;
        }

        ulUpdate(renderer);
    }

    // Debug counter
    private int renderCount = 0;

    /**
     * Render Ultralight view to OpenGL texture.
     * Call this after update(), before drawing the quad.
     */
    public void render() {
        if (!initialized || renderer == MemoryUtil.NULL || view == MemoryUtil.NULL) {
            return;
        }

        ulRefreshDisplay(renderer, 0);
        ulRender(renderer);

        // Get surface bitmap
        long surface = ulViewGetSurface(view);
        if (surface == MemoryUtil.NULL) {
            if (renderCount++ % 100 == 0) System.err.println("[UltralightManager] Surface is NULL");
            return;
        }

        long bitmap = ulBitmapSurfaceGetBitmap(surface);
        if (bitmap == MemoryUtil.NULL) {
            if (renderCount++ % 100 == 0) System.err.println("[UltralightManager] Bitmap is NULL");
            return;
        }

        // Get bitmap properties
        int bitmapWidth = ulBitmapGetWidth(bitmap);
        int bitmapHeight = ulBitmapGetHeight(bitmap);

        if (bitmapWidth == 0 || bitmapHeight == 0) {
            if (renderCount++ % 100 == 0) System.err.println("[UltralightManager] Bitmap dimensions: " + bitmapWidth + "x" + bitmapHeight);
            return;
        }

        long pixels = ulBitmapLockPixels(bitmap);

        if (pixels != MemoryUtil.NULL) {
            // Calculate row bytes (Ultralight uses BGRA format, 4 bytes per pixel)
            int rowBytes = ulBitmapGetRowBytes(bitmap);
            long size = (long) rowBytes * bitmapHeight;

            // Create a ByteBuffer view of the pixel data
            java.nio.ByteBuffer pixelBuffer = MemoryUtil.memByteBuffer(pixels, (int) size);

            // Debug: Log pixel content at specific frames
            if (renderCount == 0 || renderCount == 10 || renderCount == 60 || renderCount == 120) {
                System.out.println("[UltralightManager] Render #" + renderCount + " - Bitmap: " + bitmapWidth + "x" + bitmapHeight);
                System.out.println("[UltralightManager] Row bytes: " + rowBytes + ", Total size: " + size);
                // Check first few pixels (BGRA format)
                if (pixelBuffer.remaining() >= 16) {
                    byte b = pixelBuffer.get(0);
                    byte g = pixelBuffer.get(1);
                    byte r = pixelBuffer.get(2);
                    byte a = pixelBuffer.get(3);
                    System.out.println("[UltralightManager] First pixel BGRA: " +
                        (b & 0xFF) + ", " + (g & 0xFF) + ", " + (r & 0xFF) + ", " + (a & 0xFF));
                    // Check a pixel in the middle
                    int midOffset = (bitmapHeight / 2) * rowBytes + (bitmapWidth / 2) * 4;
                    if (midOffset + 4 < pixelBuffer.remaining()) {
                        b = pixelBuffer.get(midOffset);
                        g = pixelBuffer.get(midOffset + 1);
                        r = pixelBuffer.get(midOffset + 2);
                        a = pixelBuffer.get(midOffset + 3);
                        System.out.println("[UltralightManager] Middle pixel BGRA: " +
                            (b & 0xFF) + ", " + (g & 0xFF) + ", " + (r & 0xFF) + ", " + (a & 0xFF));
                    }
                }
            }
            renderCount++;

            // Upload to OpenGL texture
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexture);

            // Make sure texture parameters are set
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            // Upload texture - use GL12.GL_BGRA for the format
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                bitmapWidth, bitmapHeight, 0,
                GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, pixelBuffer);

            ulBitmapUnlockPixels(bitmap);
        } else {
            if (renderCount++ % 100 == 0) System.err.println("[UltralightManager] Pixels pointer is NULL");
        }
    }

    /**
     * Get the OpenGL texture ID for rendering.
     */
    public int getTexture() {
        return glTexture;
    }

    /**
     * Resize the view.
     */
    public void resize(int newWidth, int newHeight) {
        if (!initialized || view == MemoryUtil.NULL) {
            return;
        }

        if (newWidth != width || newHeight != height) {
            this.width = newWidth;
            this.height = newHeight;
            ulViewResize(view, newWidth, newHeight);
        }
    }

    /**
     * Forward mouse move event to Ultralight.
     */
    public void fireMouseMove(int x, int y) {
        if (!initialized || view == MemoryUtil.NULL) {
            return;
        }
        ulViewFireMouseEvent(view, ulCreateMouseEvent(UL_MOUSE_EVENT_TYPE_MOUSE_MOVED, x, y, UL_MOUSE_BUTTON_NONE));
    }

    /**
     * Forward mouse button event to Ultralight.
     */
    public void fireMouseButton(int x, int y, int button, boolean pressed) {
        if (!initialized || view == MemoryUtil.NULL) {
            return;
        }
        int eventType = pressed ? UL_MOUSE_EVENT_TYPE_MOUSE_DOWN : UL_MOUSE_EVENT_TYPE_MOUSE_UP;
        int ulButton = button == 0 ? UL_MOUSE_BUTTON_LEFT :
                       button == 1 ? UL_MOUSE_BUTTON_RIGHT : UL_MOUSE_BUTTON_MIDDLE;
        ulViewFireMouseEvent(view, ulCreateMouseEvent(eventType, x, y, ulButton));
    }

    /**
     * Forward scroll event to Ultralight.
     */
    public void fireScroll(int x, int y, int deltaX, int deltaY) {
        if (!initialized || view == MemoryUtil.NULL) {
            return;
        }
        ulViewFireScrollEvent(view, ulCreateScrollEvent(UL_SCROLL_EVENT_TYPE_SCROLL_BY_PIXEL, deltaX, deltaY));
    }

    /**
     * Forward keyboard event to Ultralight.
     */
    public void fireKeyEvent(int keyCode, boolean pressed) {
        if (!initialized || view == MemoryUtil.NULL) {
            return;
        }
        int eventType = pressed ? UL_KEY_EVENT_TYPE_RAW_KEY_DOWN : UL_KEY_EVENT_TYPE_KEY_UP;
        // Note: This is simplified - full key handling would need virtual key code translation
        ulViewFireKeyEvent(view, ulCreateKeyEvent(eventType, 0, keyCode, 0, ulCreateString(""), ulCreateString(""), false, false, false));
    }

    /**
     * Forward character event to Ultralight for text input.
     */
    public void fireCharEvent(char c) {
        if (!initialized || view == MemoryUtil.NULL) {
            return;
        }
        String charStr = String.valueOf(c);
        ulViewFireKeyEvent(view, ulCreateKeyEvent(UL_KEY_EVENT_TYPE_CHAR, 0, 0, 0, ulCreateString(charStr), ulCreateString(charStr), false, false, false));
    }

    /**
     * Execute JavaScript in the view.
     */
    public void executeScript(String script) {
        if (!initialized || view == MemoryUtil.NULL) {
            return;
        }

        try {
            long str = ulCreateString(script);
            if (str != MemoryUtil.NULL) {
                // Note: ulViewEvaluateScript may have JNI issues with LWJGL 3.3.3
                // For now, skip JavaScript execution until we can verify compatibility
                System.out.println("[UltralightManager] executeScript skipped (JNI compatibility)");
                ulDestroyString(str);
            }
        } catch (Exception e) {
            System.err.println("[UltralightManager] executeScript failed: " + e.getMessage());
        }
    }

    /**
     * Clean up all resources.
     */
    public void shutdown() {
        if (!initialized) {
            return;
        }

        System.out.println("[UltralightManager] Shutting down");

        if (view != MemoryUtil.NULL) {
            ulDestroyView(view);
            view = MemoryUtil.NULL;
        }

        if (viewConfig != MemoryUtil.NULL) {
            ulDestroyViewConfig(viewConfig);
            viewConfig = MemoryUtil.NULL;
        }

        if (renderer != MemoryUtil.NULL) {
            ulDestroyRenderer(renderer);
            renderer = MemoryUtil.NULL;
        }

        if (config != MemoryUtil.NULL) {
            ulDestroyConfig(config);
            config = MemoryUtil.NULL;
        }

        if (glTexture != 0) {
            GL11.glDeleteTextures(glTexture);
            glTexture = 0;
        }

        if (cachedPixelBuffer != null) {
            MemoryUtil.memFree(cachedPixelBuffer);
            cachedPixelBuffer = null;
        }

        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    // Cached pixel data for external access - COPY of pixels to avoid locking issues
    private java.nio.ByteBuffer cachedPixelBuffer = null;
    private int cachedRowBytes = 0;
    private int cachedBitmapWidth = 0;
    private int cachedBitmapHeight = 0;

    /**
     * Get the row bytes of the current bitmap.
     */
    public int getRowBytes() {
        return cachedRowBytes;
    }

    /**
     * Copy pixel data from Ultralight bitmap to a cached buffer.
     * Call this after render() to get a safe copy of pixel data.
     */
    public void copyPixelData() {
        if (!initialized || view == MemoryUtil.NULL) {
            return;
        }

        long surface = ulViewGetSurface(view);
        if (surface == MemoryUtil.NULL) {
            return;
        }

        long bitmap = ulBitmapSurfaceGetBitmap(surface);
        if (bitmap == MemoryUtil.NULL) {
            return;
        }

        cachedBitmapWidth = ulBitmapGetWidth(bitmap);
        cachedBitmapHeight = ulBitmapGetHeight(bitmap);
        cachedRowBytes = ulBitmapGetRowBytes(bitmap);

        if (cachedBitmapWidth == 0 || cachedBitmapHeight == 0) {
            return;
        }

        long pixels = ulBitmapLockPixels(bitmap);
        if (pixels == MemoryUtil.NULL) {
            return;
        }

        int size = cachedRowBytes * cachedBitmapHeight;

        // Allocate or reallocate cached buffer if needed
        if (cachedPixelBuffer == null || cachedPixelBuffer.capacity() < size) {
            if (cachedPixelBuffer != null) {
                MemoryUtil.memFree(cachedPixelBuffer);
            }
            cachedPixelBuffer = MemoryUtil.memAlloc(size);
        }

        // Copy pixel data to our buffer
        java.nio.ByteBuffer srcBuffer = MemoryUtil.memByteBuffer(pixels, size);
        cachedPixelBuffer.clear();
        cachedPixelBuffer.put(srcBuffer);
        cachedPixelBuffer.flip();

        ulBitmapUnlockPixels(bitmap);
    }

    /**
     * Get the cached pixel buffer (safe copy from last copyPixelData call).
     */
    public java.nio.ByteBuffer getPixelBuffer() {
        return cachedPixelBuffer;
    }

    /**
     * Get the cached bitmap width.
     */
    public int getBitmapWidth() {
        return cachedBitmapWidth;
    }

    /**
     * Get the cached bitmap height.
     */
    public int getBitmapHeight() {
        return cachedBitmapHeight;
    }

    /**
     * Load a resource file as a string.
     */
    private String loadResourceAsString(String path) {
        try {
            // Try loading from class loader
            InputStream is = Minecraft.getInstance().getClass().getClassLoader()
                .getResourceAsStream(path);

            if (is == null) {
                // Try alternative paths
                is = getClass().getResourceAsStream("/" + path);
            }

            if (is != null) {
                byte[] bytes = is.readAllBytes();
                is.close();
                return new String(bytes, StandardCharsets.UTF_8);
            }

            System.err.println("[UltralightManager] Resource not found: " + path);
            return null;

        } catch (Exception e) {
            System.err.println("[UltralightManager] Error loading resource: " + e.getMessage());
            return null;
        }
    }

    // Ultralight event type constants (should match C enum)
    private static final int UL_MOUSE_EVENT_TYPE_MOUSE_MOVED = 0;
    private static final int UL_MOUSE_EVENT_TYPE_MOUSE_DOWN = 1;
    private static final int UL_MOUSE_EVENT_TYPE_MOUSE_UP = 2;

    private static final int UL_MOUSE_BUTTON_NONE = 0;
    private static final int UL_MOUSE_BUTTON_LEFT = 1;
    private static final int UL_MOUSE_BUTTON_MIDDLE = 2;
    private static final int UL_MOUSE_BUTTON_RIGHT = 3;

    private static final int UL_SCROLL_EVENT_TYPE_SCROLL_BY_PIXEL = 0;
    private static final int UL_SCROLL_EVENT_TYPE_SCROLL_BY_PAGE = 1;

    private static final int UL_KEY_EVENT_TYPE_KEY_DOWN = 0;
    private static final int UL_KEY_EVENT_TYPE_KEY_UP = 1;
    private static final int UL_KEY_EVENT_TYPE_RAW_KEY_DOWN = 2;
    private static final int UL_KEY_EVENT_TYPE_CHAR = 3;
}
