package com.BookKeeper.InventoryNetwork.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

/**
 * Animated Hello World overlay with fancy effects.
 */
public class HelloWorldOverlay {
    private static boolean visible = false;
    private static long showTime = 0;

    public static void toggle() {
        visible = !visible;
        if (visible) {
            showTime = System.currentTimeMillis();
        }
    }

    public static boolean isVisible() {
        return visible;
    }

    public static void render(GuiGraphics guiGraphics) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Time-based animation
        long elapsed = System.currentTimeMillis() - showTime;
        float time = elapsed / 1000f;

        // Floating animation (sine wave)
        float floatOffset = (float) Math.sin(time * 2.0) * 8f;

        // Pulsing scale effect (for the glow)
        float pulse = (float) (0.5 + 0.5 * Math.sin(time * 3.0));

        // Rainbow hue rotation
        float hue = (time * 0.1f) % 1.0f;

        String text = "Hello World!";
        int textWidth = font.width(text);
        int textHeight = font.lineHeight;

        // Box dimensions
        int boxPadding = 30;
        int boxWidth = textWidth + boxPadding * 2;
        int boxHeight = textHeight + boxPadding * 2 + 40; // Extra space for subtitle

        // Center position with float animation
        int boxX = (screenWidth - boxWidth) / 2;
        int boxY = (int) ((screenHeight - boxHeight) / 2 + floatOffset);

        // Draw animated glow layers (outer to inner)
        int glowLayers = 8;
        for (int i = glowLayers; i >= 0; i--) {
            float glowPulse = pulse * 0.5f + 0.5f;
            int expand = (int) (i * 3 * glowPulse);
            int alpha = (int) (40 - i * 4);
            int[] rgb = hsvToRgb(hue, 0.8f, 1.0f);
            int glowColor = (alpha << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            guiGraphics.fill(
                boxX - expand, boxY - expand,
                boxX + boxWidth + expand, boxY + boxHeight + expand,
                glowColor
            );
        }

        // Draw main background with gradient effect (simulated with horizontal strips)
        int strips = boxHeight;
        for (int i = 0; i < strips; i++) {
            float stripHue = (hue + (float) i / strips * 0.3f) % 1.0f;
            int[] rgb = hsvToRgb(stripHue, 0.3f, 0.1f);
            int stripColor = 0xEE000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            guiGraphics.fill(boxX, boxY + i, boxX + boxWidth, boxY + i + 1, stripColor);
        }

        // Draw animated border
        int borderThickness = 3;
        float borderHue = (hue + 0.5f) % 1.0f;
        int[] borderRgb = hsvToRgb(borderHue, 1.0f, 1.0f);
        int borderColor = 0xFF000000 | (borderRgb[0] << 16) | (borderRgb[1] << 8) | borderRgb[2];

        // Top border
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + borderThickness, borderColor);
        // Bottom border
        guiGraphics.fill(boxX, boxY + boxHeight - borderThickness, boxX + boxWidth, boxY + boxHeight, borderColor);
        // Left border
        guiGraphics.fill(boxX, boxY, boxX + borderThickness, boxY + boxHeight, borderColor);
        // Right border
        guiGraphics.fill(boxX + boxWidth - borderThickness, boxY, boxX + boxWidth, boxY + boxHeight, borderColor);

        // Draw corner accents (animated)
        int cornerSize = 10;
        int[] cornerRgb = hsvToRgb((hue + 0.25f) % 1.0f, 1.0f, 1.0f);
        int cornerColor = 0xFF000000 | (cornerRgb[0] << 16) | (cornerRgb[1] << 8) | cornerRgb[2];

        // Top-left corner
        guiGraphics.fill(boxX, boxY, boxX + cornerSize, boxY + borderThickness + 2, cornerColor);
        guiGraphics.fill(boxX, boxY, boxX + borderThickness + 2, boxY + cornerSize, cornerColor);

        // Top-right corner
        guiGraphics.fill(boxX + boxWidth - cornerSize, boxY, boxX + boxWidth, boxY + borderThickness + 2, cornerColor);
        guiGraphics.fill(boxX + boxWidth - borderThickness - 2, boxY, boxX + boxWidth, boxY + cornerSize, cornerColor);

        // Bottom-left corner
        guiGraphics.fill(boxX, boxY + boxHeight - borderThickness - 2, boxX + cornerSize, boxY + boxHeight, cornerColor);
        guiGraphics.fill(boxX, boxY + boxHeight - cornerSize, boxX + borderThickness + 2, boxY + boxHeight, cornerColor);

        // Bottom-right corner
        guiGraphics.fill(boxX + boxWidth - cornerSize, boxY + boxHeight - borderThickness - 2, boxX + boxWidth, boxY + boxHeight, cornerColor);
        guiGraphics.fill(boxX + boxWidth - borderThickness - 2, boxY + boxHeight - cornerSize, boxX + boxWidth, boxY + boxHeight, cornerColor);

        // Draw main text with rainbow effect (each letter different color)
        int textX = boxX + (boxWidth - textWidth) / 2;
        int textY = boxY + boxPadding;

        // Draw text shadow
        for (int i = 0; i < text.length(); i++) {
            String letter = String.valueOf(text.charAt(i));
            int letterX = textX + font.width(text.substring(0, i));
            guiGraphics.drawString(font, letter, letterX + 2, textY + 2, 0xFF000000, false);
        }

        // Draw rainbow text
        for (int i = 0; i < text.length(); i++) {
            String letter = String.valueOf(text.charAt(i));
            float letterHue = (hue + (float) i / text.length()) % 1.0f;
            int[] letterRgb = hsvToRgb(letterHue, 1.0f, 1.0f);
            int letterColor = 0xFF000000 | (letterRgb[0] << 16) | (letterRgb[1] << 8) | letterRgb[2];
            int letterX = textX + font.width(text.substring(0, i));
            guiGraphics.drawString(font, letter, letterX, textY, letterColor, false);
        }

        // Draw subtitle with typing animation
        String subtitle = "Press H to close";
        int visibleChars = (int) Math.min(subtitle.length(), elapsed / 50);
        String visibleSubtitle = subtitle.substring(0, visibleChars);

        int subtitleWidth = font.width(visibleSubtitle);
        int subtitleX = boxX + (boxWidth - font.width(subtitle)) / 2;
        int subtitleY = textY + textHeight + 20;

        // Blinking cursor
        String cursor = ((elapsed / 500) % 2 == 0) ? "_" : "";
        guiGraphics.drawString(font, visibleSubtitle + cursor, subtitleX, subtitleY, 0xFFAAAAAA, false);

        // Draw animated particles (floating dots)
        int particleCount = 12;
        for (int i = 0; i < particleCount; i++) {
            float particleTime = time + i * 0.5f;
            float px = boxX + (float) (boxWidth * (0.5 + 0.4 * Math.sin(particleTime * 0.7 + i)));
            float py = boxY + (float) (boxHeight * (0.5 + 0.4 * Math.cos(particleTime * 0.9 + i * 0.7)));
            float particleHue = (hue + i * 0.08f) % 1.0f;
            int[] particleRgb = hsvToRgb(particleHue, 1.0f, 1.0f);
            float particleAlpha = (float) (0.3 + 0.3 * Math.sin(particleTime * 2));
            int particleColor = ((int)(particleAlpha * 255) << 24) | (particleRgb[0] << 16) | (particleRgb[1] << 8) | particleRgb[2];

            int size = 2 + (i % 3);
            guiGraphics.fill((int) px, (int) py, (int) px + size, (int) py + size, particleColor);
        }
    }

    /**
     * Convert HSV to RGB.
     */
    private static int[] hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        return new int[]{(int) (r * 255), (int) (g * 255), (int) (b * 255)};
    }
}
