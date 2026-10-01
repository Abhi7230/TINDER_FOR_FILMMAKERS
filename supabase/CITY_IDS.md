# City and area ids (shared by LIVE seed.sql and the DEMO seed)

Rule: city id = `snake(name)`; area id = `cityId + "_" + snake(area name)`, where `snake` lowercases, turns every run of
non-alphanumeric characters into one `_`, and trims `_` at the ends ("T. Nagar" → `t_nagar`, "Noida Sec 18" → `noida_sec_18`).

`is_live` = Mumbai, Bengaluru. `is_early` = the 8 early Indian cities (one "City centre" area each). Currency, paid floor/day
(placeholder, R04 only), emergency number and time zone are in `seed.sql`.

| City id | Name | Country · currency · floor | Live / early | Area ids |
|---|---|---|---|---|
| `mumbai` | Mumbai | IN · INR · 2,000 | live | `mumbai_andheri_west`, `mumbai_versova`, `mumbai_bandra`, `mumbai_juhu`, `mumbai_goregaon_film_city`, `mumbai_malad`, `mumbai_dadar`, `mumbai_powai`, `mumbai_lower_parel`, `mumbai_thane` |
| `bengaluru` | Bengaluru | IN · INR · 2,000 | live | `bengaluru_indiranagar`, `bengaluru_koramangala`, `bengaluru_jayanagar`, `bengaluru_hsr_layout`, `bengaluru_whitefield`, `bengaluru_malleshwaram`, `bengaluru_jp_nagar`, `bengaluru_electronic_city` |
| `delhi_ncr` | Delhi NCR | IN · INR · 2,000 |  | `delhi_ncr_saket`, `delhi_ncr_hauz_khas`, `delhi_ncr_dwarka`, `delhi_ncr_lajpat_nagar`, `delhi_ncr_noida_sec_18`, `delhi_ncr_gurugram_cyber_city` |
| `pune` | Pune | IN · INR · 1,500 |  | `pune_koregaon_park`, `pune_kothrud`, `pune_baner`, `pune_viman_nagar` |
| `hyderabad` | Hyderabad | IN · INR · 1,500 |  | `hyderabad_banjara_hills`, `hyderabad_jubilee_hills`, `hyderabad_gachibowli`, `hyderabad_ameerpet` |
| `chennai` | Chennai | IN · INR · 1,500 |  | `chennai_t_nagar`, `chennai_adyar`, `chennai_velachery`, `chennai_anna_nagar` |
| `kolkata` | Kolkata | IN · INR · 1,200 |  | `kolkata_salt_lake`, `kolkata_park_street`, `kolkata_jadavpur`, `kolkata_tollygunge` |
| `kochi` | Kochi | IN · INR · 1,200 |  | `kochi_kakkanad`, `kochi_edappally`, `kochi_fort_kochi` |
| `los_angeles` | Los Angeles | US · USD · 150 |  | `los_angeles_hollywood`, `los_angeles_burbank`, `los_angeles_silver_lake`, `los_angeles_downtown`, `los_angeles_culver_city` |
| `atlanta` | Atlanta | US · USD · 150 |  | `atlanta_midtown`, `atlanta_decatur`, `atlanta_west_end` |
| `london` | London | GB · GBP · 120 |  | `london_soho`, `london_shoreditch`, `london_hackney`, `london_brixton`, `london_ealing` |
| `manchester` | Manchester | GB · GBP · 120 |  | `manchester_northern_quarter`, `manchester_salford_quays`, `manchester_didsbury` |
| `indore` | Indore | IN · INR · 1,000 | early | `indore_city_centre` |
| `dehradun` | Dehradun | IN · INR · 1,000 | early | `dehradun_city_centre` |
| `nagpur` | Nagpur | IN · INR · 1,000 | early | `nagpur_city_centre` |
| `chandigarh` | Chandigarh | IN · INR · 1,000 | early | `chandigarh_city_centre` |
| `ahmedabad` | Ahmedabad | IN · INR · 1,000 | early | `ahmedabad_city_centre` |
| `jaipur` | Jaipur | IN · INR · 1,000 | early | `jaipur_city_centre` |
| `lucknow` | Lucknow | IN · INR · 1,000 | early | `lucknow_city_centre` |
| `mysuru` | Mysuru | IN · INR · 1,000 | early | `mysuru_city_centre` |
