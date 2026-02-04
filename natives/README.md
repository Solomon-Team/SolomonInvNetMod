# Ultralight Native Libraries Setup

The Ultralight HTML rendering engine requires native libraries (DLLs on Windows) to function.

## Download Instructions

1. Go to https://ultralig.ht/download/
2. Download the **Ultralight SDK** for Windows x64
3. Extract the archive
4. Copy the following files from the SDK's `bin/` directory to `natives/windows-x64/`:
   - `Ultralight.dll`
   - `UltralightCore.dll`
   - `WebCore.dll`
   - `AppCore.dll`

5. Also copy the `resources/` folder from the SDK to `natives/windows-x64/resources/`
   This contains essential runtime files like:
   - `cacert.pem`
   - `icudt67l.dat`

## Directory Structure

After setup, your natives folder should look like:

```
natives/
├── README.md
└── windows-x64/
    ├── Ultralight.dll
    ├── UltralightCore.dll
    ├── WebCore.dll
    ├── AppCore.dll
    └── resources/
        ├── cacert.pem
        ├── icudt67l.dat
        └── ...
```

## Running the Mod

When running the Minecraft client with this mod, ensure the native library path includes the natives directory.

For development (runClient), the mod will automatically look for natives in the expected locations.

For production, the DLLs need to be bundled or placed in the Minecraft instance's native library path.

## Troubleshooting

If you see errors like "UnsatisfiedLinkError" or "Cannot load native library", ensure:
1. All DLLs are present in the natives folder
2. The resources folder is present
3. You're using the correct architecture (x64)
4. Visual C++ Redistributable 2019 or later is installed
