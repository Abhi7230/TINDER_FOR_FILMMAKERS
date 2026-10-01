package com.tff.app.data.demo

import com.tff.app.model.Area
import com.tff.app.model.City

/**
 * Reference data (PRD §11.2): 20 cities, 67 areas. Ids follow the convention shared with the Supabase
 * seed: city id = lowercase snake of the name ("delhi_ncr"), area id = cityId + "_" + snake(area name)
 * ("mumbai_goregaon_film_city"). Early cities have one "City centre" area at the city coordinates.
 */
object DemoCities {

    fun snake(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private fun city(
        name: String, country: String, currency: String, lat: Double, lng: Double, floor: Int,
        areas: List<Triple<String, Double, Double>>, live: Boolean = false, early: Boolean = false,
    ): City {
        val id = snake(name)
        val emergency = when (country) { "IN" -> "112"; "US" -> "911"; else -> "999" }
        return City(
            id = id, name = name, country = country, currency = currency, lat = lat, lng = lng,
            paidFloorDay = floor, emergencyNumber = emergency, isLive = live, isEarly = early,
            areas = areas.map { (an, alat, alng) -> Area(id + "_" + snake(an), id, an, alat, alng) },
        )
    }

    private fun early(name: String, lat: Double, lng: Double): City =
        city(name, "IN", "INR", lat, lng, 1000, listOf(Triple("City centre", lat, lng)), early = true)

    /** Live cities first, then the other listed cities, then early cities. */
    val all: List<City> = listOf(
        city("Mumbai", "IN", "INR", 19.076, 72.878, 2000, listOf(
            Triple("Andheri West", 19.136, 72.830), Triple("Versova", 19.131, 72.815), Triple("Bandra", 19.060, 72.830),
            Triple("Juhu", 19.108, 72.826), Triple("Goregaon Film City", 19.159, 72.889), Triple("Malad", 19.187, 72.849),
            Triple("Dadar", 19.018, 72.848), Triple("Powai", 19.118, 72.906), Triple("Lower Parel", 18.996, 72.830),
            Triple("Thane", 19.218, 72.978),
        ), live = true),
        city("Bengaluru", "IN", "INR", 12.972, 77.595, 2000, listOf(
            Triple("Indiranagar", 12.978, 77.641), Triple("Koramangala", 12.935, 77.625), Triple("Jayanagar", 12.925, 77.594),
            Triple("HSR Layout", 12.912, 77.645), Triple("Whitefield", 12.970, 77.750), Triple("Malleshwaram", 13.003, 77.570),
            Triple("JP Nagar", 12.907, 77.586), Triple("Electronic City", 12.845, 77.660),
        ), live = true),
        city("Delhi NCR", "IN", "INR", 28.614, 77.209, 2000, listOf(
            Triple("Saket", 28.525, 77.207), Triple("Hauz Khas", 28.549, 77.200), Triple("Dwarka", 28.592, 77.046),
            Triple("Lajpat Nagar", 28.568, 77.243), Triple("Noida Sec 18", 28.570, 77.321), Triple("Gurugram Cyber City", 28.495, 77.089),
        )),
        city("Pune", "IN", "INR", 18.520, 73.857, 1500, listOf(
            Triple("Koregaon Park", 18.536, 73.894), Triple("Kothrud", 18.507, 73.808), Triple("Baner", 18.559, 73.787),
            Triple("Viman Nagar", 18.567, 73.914),
        )),
        city("Hyderabad", "IN", "INR", 17.385, 78.487, 1500, listOf(
            Triple("Banjara Hills", 17.416, 78.438), Triple("Jubilee Hills", 17.431, 78.407), Triple("Gachibowli", 17.440, 78.349),
            Triple("Ameerpet", 17.437, 78.448),
        )),
        city("Chennai", "IN", "INR", 13.083, 80.271, 1500, listOf(
            Triple("T. Nagar", 13.042, 80.234), Triple("Adyar", 13.001, 80.257), Triple("Velachery", 12.976, 80.221),
            Triple("Anna Nagar", 13.085, 80.210),
        )),
        city("Kolkata", "IN", "INR", 22.573, 88.364, 1200, listOf(
            Triple("Salt Lake", 22.580, 88.415), Triple("Park Street", 22.553, 88.352), Triple("Jadavpur", 22.499, 88.371),
            Triple("Tollygunge", 22.498, 88.346),
        )),
        city("Kochi", "IN", "INR", 9.931, 76.267, 1200, listOf(
            Triple("Kakkanad", 10.016, 76.341), Triple("Edappally", 10.025, 76.308), Triple("Fort Kochi", 9.966, 76.242),
        )),
        city("Los Angeles", "US", "USD", 34.052, -118.244, 150, listOf(
            Triple("Hollywood", 34.093, -118.329), Triple("Burbank", 34.181, -118.309), Triple("Silver Lake", 34.087, -118.270),
            Triple("Downtown", 34.040, -118.247), Triple("Culver City", 34.021, -118.396),
        )),
        city("Atlanta", "US", "USD", 33.749, -84.388, 150, listOf(
            Triple("Midtown", 33.781, -84.383), Triple("Decatur", 33.775, -84.296), Triple("West End", 33.736, -84.413),
        )),
        city("London", "GB", "GBP", 51.507, -0.128, 120, listOf(
            Triple("Soho", 51.514, -0.136), Triple("Shoreditch", 51.526, -0.078), Triple("Hackney", 51.545, -0.055),
            Triple("Brixton", 51.461, -0.116), Triple("Ealing", 51.513, -0.305),
        )),
        city("Manchester", "GB", "GBP", 53.481, -2.243, 120, listOf(
            Triple("Northern Quarter", 53.484, -2.236), Triple("Salford Quays", 53.471, -2.296), Triple("Didsbury", 53.416, -2.231),
        )),
        early("Indore", 22.719, 75.857),
        early("Dehradun", 30.317, 78.032),
        early("Nagpur", 21.146, 79.088),
        early("Chandigarh", 30.733, 76.779),
        early("Ahmedabad", 23.023, 72.571),
        early("Jaipur", 26.912, 75.787),
        early("Lucknow", 26.847, 80.947),
        early("Mysuru", 12.296, 76.639),
    )

    private val byId: Map<String, City> = all.associateBy { it.id }
    private val areasById: Map<String, Area> = all.flatMap { it.areas }.associateBy { it.id }

    fun city(id: String): City? = byId[id]
    fun area(id: String): Area? = areasById[id]
    fun cityOfArea(areaId: String): City? = areasById[areaId]?.let { byId[it.cityId] }
}
