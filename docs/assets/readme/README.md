# README Image Assets

These files support the visual entry points in the repository README.

| File | Purpose |
| --- | --- |
| `title-robot-facing-user.png` | Rendered title icon used directly in the README heading. |
| `button-new-library-light.png` | Light-mode rendered button used directly by the README for the new-library issue flow. |
| `button-new-library-dark.png` | Dark-mode rendered button used directly by the README for the new-library issue flow. |
| `button-update-metadata-light.png` | Light-mode rendered button used directly by the README for the existing-library update issue flow. |
| `button-update-metadata-dark.png` | Dark-mode rendered button used directly by the README for the existing-library update issue flow. |
| `button-new-library.svg` | Editable source for the new-library button. The README uses the rendered PNG variants instead. |
| `button-update-metadata.svg` | Editable source for the existing-library update button. The README uses the rendered PNG variants instead. |
| `library-new-icon.png` | Seed icon embedded in `button-new-library.svg` and used when regenerating the rendered button images. |
| `library-update-icon.png` | Seed icon embedded in `button-update-metadata.svg` and used when regenerating the rendered button images. |
| `title-robot.png` | Seed title icon retained so the rendered title image can be regenerated or adjusted later. |

The README intentionally references the PNG button variants rather than the
source SVGs, because GitHub README rendering is more reliable with plain image
files for these composed buttons and their light/dark variants.
