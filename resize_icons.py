from PIL import Image
import os

source = r"f:\Piyush Work\Software Making\Gmail Otp Syncer\android\app\src\main\res\mipmap-xxxhdpi\ic_launcher.png"
target_dir = r"f:\Piyush Work\Software Making\Gmail Otp Syncer\extension"

sizes = [16, 48, 128]

try:
    img = Image.open(source)
    for size in sizes:
        resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
        resized_img.save(os.path.join(target_dir, f"icon{size}.png"))
    print("Icons generated successfully!")
except Exception as e:
    print(f"Error: {e}")
