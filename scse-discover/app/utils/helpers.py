from datetime import datetime

def generate_timestamp():
    return datetime.now().strftime("%Y%m%d_%H%M%S")

def get_file_path(prefix, extension):
    timestamp = generate_timestamp()
    return f"data/{prefix}_{timestamp}.{extension}"
