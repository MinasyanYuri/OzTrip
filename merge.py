import os

# Путь только к вашей папке с Java-кодом
java_path = './app/src/main/java/com/example/oztrip'

all_java_files = []

# Находим абсолютно все файлы .java
if os.path.exists(java_path):
    for root, dirs, files in os.walk(java_path):
        for f in files:
            if f.endswith('.java'):
                all_java_files.append(os.path.join(root, f))

total_files = len(all_java_files)

if total_files == 0:
    print("Ошибка: Java-файлы не найдены! Проверьте путь к папке.")
else:
    # Делим список строго на 7 частей
    parts_count = 7
    chunk_size = (total_files + (parts_count - 1)) // parts_count

    for i in range(parts_count):
        start = i * chunk_size
        end = min(start + chunk_size, total_files)
        part_files = all_java_files[start:end]

        if not part_files:
            continue

        # Файлы будут называться java_part1.txt, java_part2.txt и т.д.
        with open(f"java_part{i+1}.txt", "w", encoding="utf-8") as outfile:
            for file_path in part_files:
                outfile.write(f"\n\n// --- FILE: {file_path} ---\n\n")
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as infile:
                    outfile.write(infile.read())
    print(f"Успешно! Создано частей: {parts_count}. Всего Java-файлов найдено: {total_files}")
