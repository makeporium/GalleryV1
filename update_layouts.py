import os
import re
import glob

directory = r'c:\Users\NEW\AndroidStudioProjects\GalleryV1\app\src\main\res\layout'
files = glob.glob(os.path.join(directory, '*.xml'))

for file in files:
    with open(file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Text colors
    content = re.sub(r'android:textColor="#[0-9A-Fa-f]{6}"', 'android:textColor="@color/pastel_text_primary"', content)
    content = re.sub(r'android:textColorHint="#[0-9A-Fa-f]{6}"', 'android:textColorHint="@color/pastel_text_secondary"', content)
    
    # Backgrounds
    content = re.sub(r'android:background="#[0-9A-Fa-f]{6}"', 'android:background="@color/pastel_bg_lavender"', content, count=1)
    content = re.sub(r'android:background="#[0-9A-Fa-f]{6}"', 'android:background="@color/pastel_surface_card"', content)
    
    # Card Backgrounds
    content = re.sub(r'app:cardBackgroundColor="#[0-9A-Fa-f]{6}"', 'app:cardBackgroundColor="@color/pastel_surface_card"', content)

    with open(file, 'w', encoding='utf-8') as f:
        f.write(content)

print('Update remaining layouts')
