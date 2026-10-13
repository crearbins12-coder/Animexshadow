import json
import os
import requests

# Endpoints descubiertos de Shademanga
BASE_URL = "https://shademanga.com"
RECENT_CHAPTERS_ENDPOINT = f"{BASE_URL}/series-locales/capitulos/recientes"
SEARCH_INDEX_ENDPOINT = f"{BASE_URL}/series-locales/search-index"

def fetch_shademanga_data():
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer": "https://shademanga.com/"
    }
    
    print("Iniciando sincronización con Shademanga...")
    
    try:
        # Intentar obtener los capítulos recientes
        response = requests.get(RECENT_CHAPTERS_ENDPOINT, headers=headers, timeout=15)
        
        if response.status_code == 200:
            data = response.json()
            print(f"¡Éxito! Se obtuvieron datos recientes correctamente.")
            
            # Guardar el resultado en un archivo JSON dentro del repo (opcional)
            os.makedirs("output", exist_ok=True)
            output_file = "output/recent_content.json"
            with open(output_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=4)
            print(f"Datos guardados en {output_file}")
        else:
            print(f"El servidor respondió con el código: {response.status_code}")
            
    except Exception as e:
        print(f"Error al conectar con la API: {e}")

if __name__ == "__main__":
    fetch_shademanga_data()
