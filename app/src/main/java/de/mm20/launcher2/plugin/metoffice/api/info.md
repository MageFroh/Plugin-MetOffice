API documentation is here: https://datahub.metoffice.gov.uk/docs/f/category/site-specific/overview.

There is no name-to-location service, but the Met Office web app uses https://www.metoffice.gov.uk/plain-rest-services/location-search?searchTerm=<location>&max=5&filter=exclude-marine-offshore
Otherwise, there is also https://developer.android.com/reference/android/location/Geocoder.html

Forecast is accessible online via https://www.metoffice.gov.uk/weather/forecast/<geohash>, with geohash as per https://www.movable-type.co.uk/scripts/geohash.html.
See https://github.com/drfonfon/android-kotlin-geohash
