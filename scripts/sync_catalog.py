import json
import os
import cloudscraper

BASE_URL = "https://shademanga.com"
RECENT_CHAPTERS_ENDPOINT = f"{BASE_URL}/series-locales/capitulos/recientes"

def fetch_shademanga_data():
    # Inicializamos cloudscraper para que evada Cloudflare automáticamente
    scraper = cloudscraper.create_scraper(
        browser={
            'browser': 'chrome',
            'platform': 'android',
            'desktop': False
        }
    )
    
    print(f"Intentando conectar (bypass Cloudflare) a: {RECENT_CHAPTERS_ENDPOINT}")
    
    try:
        response = scraper.get(RECENT_CHAPTERS_ENDPOINT, timeout=20)
        
        print(f"Código de estado HTTP: {response.status_code}")
        print(f"Contenido recibido (primeros 500 caracteres): {response.text[:500]}")
        
        if response.status_code == 200:
            # Intentamos parsear el JSON
            data = response.json()
            os.makedirs("output", exist_ok=True)
            output_file = "output/recent_content.json"
            with open(output_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=4)
            print(f"¡Datos guardados con éxito en {output_file}!")
        else:
            print(f"El servidor respondió con un código inesperado: {response.status_code}")
            
    except Exception as e:
        print(f"Ocurrió un error al intentar sortear Cloudflare: {e}")

if __name__ == "__main__":
    fetch_shademanga_data()
