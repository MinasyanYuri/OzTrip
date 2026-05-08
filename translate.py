import os
import xml.etree.ElementTree as ET
from googletrans import Translator

# Путь к вашему основному strings.xml
input_file = "app/src/main/res/values-ru/strings.xml"
# Языки для перевода (можно добавить свои)
langs = {
    "en": "en",   # русский
    # "es": "es", # пример испанского
}

translator = Translator()
tree = ET.parse(input_file)
root = tree.getroot()

for lang_code, lang_folder in langs.items():
    out_dir = f"app/src/main/res/values-{lang_folder}"
    os.makedirs(out_dir, exist_ok=True)

    new_root = ET.Element("resources")
    for string_elem in root.findall("string"):
        name = string_elem.get("name")
        text = string_elem.text
        if text:
            try:
                translated = translator.translate(text, dest=lang_code).text
            except Exception as e:
                print(f"Ошибка перевода '{text}': {e}")
                translated = text
        else:
            translated = ""

        new_string = ET.SubElement(new_root, "string", {"name": name})
        new_string.text = translated

    ET.indent(new_root, "    ")
    out_tree = ET.ElementTree(new_root)
    out_tree.write(os.path.join(out_dir, "strings.xml"), encoding="utf-8", xml_declaration=True)
    print(f"✅ {lang_code}: создан {out_dir}/strings.xml")