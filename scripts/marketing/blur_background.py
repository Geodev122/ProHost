"""Auth screen background (1.0.46): a practice-room photo, pre-blurred so it works on every API level.

    python3 scripts/marketing/blur_background.py [marketing/backgrounds/coaching.jpg]

Writes app/src/main/res/drawable-nodpi/auth_background.webp (540x960, Gaussian blur 16, WebP q60). AuthStepScaffold draws it
full-bleed under a surface scrim, so text and fields keep their contrast in light and dark.
"""
import sys
from PIL import Image, ImageFilter

src = sys.argv[1] if len(sys.argv) > 1 else "marketing/backgrounds/coaching.jpg"
out = "app/src/main/res/drawable-nodpi/auth_background.webp"
im = Image.open(src).convert("RGB").resize((540, 960), Image.LANCZOS).filter(ImageFilter.GaussianBlur(16))
im.save(out, "WEBP", quality=60, method=6)
print(out)
