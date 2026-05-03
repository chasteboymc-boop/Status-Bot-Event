import os
import re
import colorama
from colorama import Fore, Style

# Khởi tạo màu sắc cho terminal
colorama.init(autoreset=True)

# Các thư mục cần bỏ qua để tăng tốc và tránh báo lỗi giả
IGNORE_DIRS = {'.git', '.gradle', '.idea', 'build', 'node_modules', 'venv', '__pycache__', 'loom-cache'}
# Các đuôi file không cần quét (ảnh, file nén, file build...)
IGNORE_EXTS = {'.jar', '.exe', '.dll', '.png', '.jpg', '.class', '.zip', '.tar', '.gz'}

# Regex tìm kiếm Token và Secret
SECRET_PATTERNS = {
    "Discord Bot Token": r"(?i)(?:bot\s*)?[M|N|O][a-zA-Z0-9_-]{23,28}\.[a-zA-Z0-9_-]{6,7}\.[a-zA-Z0-9_-]{27,}",
    "Generic API Key / Password": r"(?i)(api_key|apikey|secret|token|password|auth)[\s]*[:=][\s]*['\"][a-zA-Z0-9_\-\.]{15,}['\"]",
    "Private Key": r"-----BEGIN [A-Z ]+ PRIVATE KEY-----"
}

# Regex tìm kiếm code khả nghi (thường bị lợi dụng làm backdoor)
SUSPICIOUS_PATTERNS = {
    "Thực thi code động (eval/exec)": r"\b(eval|exec)\s*\(",
    "Chạy lệnh hệ thống (os/subprocess)": r"\b(os\.system|subprocess\.(Popen|call|run|check_output))\s*\(",
    "Giải mã chuỗi ẩn (base64)": r"\bbase64\.b64decode\s*\("
}

def scan_file(filepath):
    secrets_found = []
    suspicious_found = []

    try:
        # Mở file với encoding utf-8, bỏ qua các ký tự lỗi để không làm sập script
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            lines = f.readlines()

        for line_num, line in enumerate(lines, 1):
            # Quét token
            for name, pattern in SECRET_PATTERNS.items():
                if re.search(pattern, line):
                    secrets_found.append((line_num, name, line.strip()[:100])) # Cắt ngắn dòng để tránh in tràn màn hình
            
            # Quét backdoor
            for name, pattern in SUSPICIOUS_PATTERNS.items():
                if re.search(pattern, line):
                    suspicious_found.append((line_num, name, line.strip()[:100]))

    except Exception as e:
        print(f"{Fore.YELLOW}[!] Lỗi khi đọc file {filepath}: {e}")

    return secrets_found, suspicious_found

def main():
    print(f"{Fore.CYAN}==================================================")
    print(f"{Fore.CYAN}       BẮT ĐẦU QUÉT BẢO MẬT SOURCE CODE")
    print(f"{Fore.CYAN}==================================================\n")

    current_dir = os.getcwd()
    total_files = 0
    issues_count = 0

    for root, dirs, files in os.walk(current_dir):
        # Loại bỏ các thư mục không cần quét
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]

        for file in files:
            # Bỏ qua chính file scanner này và các file binary
            if file == "security_scanner.py" or any(file.endswith(ext) for ext in IGNORE_EXTS):
                continue
            
            filepath = os.path.join(root, file)
            total_files += 1
            
            secrets, suspicious = scan_file(filepath)

            if secrets or suspicious:
                rel_path = os.path.relpath(filepath, current_dir)
                print(f"{Fore.MAGENTA}📁 Phân tích file: {rel_path}")
                
                for line_num, issue_type, line_content in secrets:
                    print(f"   {Fore.RED}[CẢNH BÁO SECRET] {Fore.WHITE}Dòng {line_num} | {Fore.RED}{issue_type}")
                    print(f"   {Fore.LIGHTBLACK_EX}> {line_content}")
                    issues_count += 1
                
                for line_num, issue_type, line_content in suspicious:
                    print(f"   {Fore.YELLOW}[NGHI VẤN BACKDOOR] {Fore.WHITE}Dòng {line_num} | {Fore.YELLOW}{issue_type}")
                    print(f"   {Fore.LIGHTBLACK_EX}> {line_content}")
                    issues_count += 1
                
                print("-" * 50)

    print(f"\n{Fore.CYAN}==================================================")
    print(f"{Fore.CYAN}Đã quét xong {total_files} file.")
    if issues_count == 0:
        print(f"{Fore.GREEN}Tuyệt vời! Không phát hiện cấu trúc nguy hiểm hay token lộ lọt.")
    else:
        print(f"{Fore.RED}Phát hiện {issues_count} vấn đề. Vui lòng kiểm tra lại các file trên!")

if __name__ == "__main__":
    main()