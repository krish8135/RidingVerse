<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>RidingVerse Map</title>
    <link
        rel="stylesheet"
        href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
    />
    <style>
        html, body {
            margin: 0;
            height: 100%;
            background: #0a0c0e;
            font-family: sans-serif;
        }
        #map {
            width: 100%;
            height: 100%;
        }
    </style>
</head>
<body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
    const map = L.map('map', {
        zoomControl: true,
        attributionControl: true
    }).setView([35.145, -83.438], 9);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors'
    }).addTo(map);

    const start = [35.145, -83.438];
    const end = [35.425, -83.918];

    L.polyline([start, end], {
        color: '#0ACF83',
        weight: 5,
        opacity: 0.9
    }).addTo(map);

    L.marker(start).addTo(map).bindPopup('Start: Tail of the Dragon');
    L.marker(end).addTo(map).bindPopup('Destination: Deal’s Gap');

    L.circleMarker([35.301, -83.670], {
        radius: 8,
        color: '#38CFFF',
        fillColor: '#38CFFF',
        fillOpacity: 0.9
    }).addTo(map).bindPopup('Lead rider');

    L.circleMarker([35.275, -83.581], {
        radius: 8,
        color: '#FFB020',
        fillColor: '#FFB020',
        fillOpacity: 0.9
    }).addTo(map).bindPopup('Sweep rider');

    map.fitBounds(L.latLngBounds([start, end]));
</script>
</body>
</html>
