import requests import time from datetime import datetime

API-Endpunkt für Abfahrtszeiten (aktualisieren mit der echten URL)

API_URL = "https://api.deinverkehrsverbund.de/abfahrtszeiten"

Funktion, um Abfahrtszeiten sicher abzurufen

def get_abfahrtszeiten(station_id): try: response = requests.get(f"{API_URL}/{station_id}", timeout=10) response.raise_for_status() return response.json() except requests.exceptions.Timeout: print("Fehler: Die Anfrage hat zu lange gedauert.") except requests.exceptions.HTTPError as e: print(f"HTTP-Fehler: {e}") except requests.exceptions.RequestException as e: print(f"Netzwerkfehler: {e}") return []

Automatische Aktualisierung der Abfahrtszeiten

def update_abfahrtszeiten_periodisch(station_id, interval=10): while True: print(f"[{datetime.now()}] Aktualisiere Abfahrtszeiten für Station {station_id}...") abfahrten = get_abfahrtszeiten(station_id)

if abfahrten:
        print(f"Neue Abfahrtszeiten: {abfahrten}")
            else:
                    print("Keine Abfahrtszeiten verfügbar.")
                        
                            time.sleep(interval * 60)  # Wartezeit in Minuten

                            Beispielstation und Start des Skripts

                            if name == "main": station_id = "obstwiesen_id"  # Hier mit echter Stations-ID ersetzen update_abfahrtszeiten_periodisch(station_id, interval=10)

                            