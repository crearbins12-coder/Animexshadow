import json
import os
import requests

BASE_URL = "https://shademanga.com"
RECENT_CHAPTERS_ENDPOINT = f"{BASE_URL}/series-locales/capitulos/recientes"

def fetch_shademanga_data():
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer": "https://shademanga.com/",
        "Accept": "application/json, text/plain, */*"
    }
    
    print(f"Intentando conectar a: {RECENT_CHAPTERS_ENDPOINT}")
    
    try:
        response = requests.get(RECENT_CHAPTERS_ENDPOINT, headers=headers, timeout=15)
        
        # Imprimimos qué contestó exactamente el servidor
        print(f"Código de estado HTTP: {response.status_code}")
        print(f"Cabeceras de respuesta: {dict(response.headers)}")
        print(f"Contenido recibido (primeros 500 caracteres): {response.text[:500]}")
        
        if response.status_code == 200:
            data = response.json()
            os.makedirs("output", exist_ok=True)
            output_file = "output/recent_content.json"
            with open(output_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=4)
            print(f"¡Datos guardados con éxito en {output_file}!")
        else:
            print("El servidor rechazó o no devolvió un JSON válido.")
            
    except Exception as e:
        print(f"Ocurrió un error en la petición: {e}")

if __name__ == "__main__":
    fetch_shademanga_data()
