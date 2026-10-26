# Banner photos & wordmark fonts (v1.8)

Machine-readable provenance: `app/src/main/assets/team_photos/manifest.json`.

Kinds: `official_team_photo` = the club's published 2025 team photo; `team_photo_day_2025` = a photo from the club's 2025 team-photo-day gallery (the full-squad pose, shot from the club's gallery, not the official composite); `older_team_photo` = most recent real team photo found (older season); `stadium` = ESPN photo of the home stadium (no team photo found).

| Team | Kind | Season | Source page | Image | Note |
|---|---|---|---|---|---|
| ARI | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/22 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3970.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium State Farm Stadium |
| ATL | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/1 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/interior/5348.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Mercedes-Benz Stadium |
| BAL | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/33 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3814.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium M&T Bank Stadium |
| BUF | team_photo_day_2025 | 2025 | https://www.buffalobills.com/photos/photos-a-candid-look-at-buffalo-bills-team-photo-day | https://static.clubs.nfl.com/image/upload/w_1600,c_limit,q_90/bills/w9caedxmjfc7darfn0dm.jpg | Gallery caption: "Buffalo Bills team photo, September 27, 2025" (section of full-team pose) |
| CAR | older_team_photo | 2019 | https://www.panthers.com/photos/behind-the-scenes-at-panthers-photo-day | https://static.clubs.nfl.com/image/private/w_1600,c_limit,q_90/panthers/x2oa2i5qaufvjhrdf7pa.jpg | Fallback 1: most recent full-team photo found — 2019 photo day (gallery published 2019-11-02) |
| CHI | team_photo_day_2025 | 2025 | https://www.chicagobears.com/photos/2025-team-photo-day-behind-the-scenes-chicago-bears | https://static.clubs.nfl.com/image/upload/w_1600,c_limit,q_90/bears/jlsrofgsov2gtsv0h9y7.jpg | Behind-the-scenes shot of the full team on the 2025 team-photo risers (has the site's "Bears Behind the Scenes" bug baked in) |
| CIN | older_team_photo | 2017 | https://www.bengals.com/photos/team-picture-gallery-19314258 | https://static.clubs.nfl.com/image/private/w_1600,c_limit,q_90/bengals/zprhd27sd24r4iqogkbi.jpg | Fallback 1: most recent full-team photo found — 2017 team picture day (gallery published 2017-09-04) |
| CLE | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/5 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3679.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Huntington Bank Field |
| DAL | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/6 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3687.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium AT&T Stadium |
| DEN | official_team_photo | 2025 | https://www.denverbroncos.com/team/photo | https://static.clubs.nfl.com/image/upload/w_1600,c_limit,q_90/broncos/vlaq9xnvgseedsdwljjk.jpg | Official "2025 Denver Broncos" team photo composite (title band and name-key panel cropped off) |
| DET | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/8 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3727.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Ford Field |
| GB | team_photo_day_2025 | 2025 | https://www.packers.com/photos/behind-the-scenes-packers-take-2025-team-photo-inside-lambeau-field | https://static.clubs.nfl.com/image/upload/w_1600,c_limit,q_90/packers/t97qvbgad6gdkomiosdp.jpg | Behind-the-scenes wide shot of the full team posed for the 2025 team photo inside Lambeau Field |
| HOU | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/34 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3891.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium NRG Stadium (ESPN lists it as Reliant Stadium) |
| IND | team_photo_day_2025 | 2025 | https://www.colts.com/photos/behind-the-scenes-2025-team-photo | https://static.clubs.nfl.com/image/upload/w_1600,c_limit,q_90/colts/qs2fg4yn6y04ptakoemk.jpg | Behind-the-scenes wide shot from the 2025 team photo session |
| JAX | team_photo_day_2025 | 2025 | https://www.jaguars.com/photos/k000538-2025-team-picture-day-behind-the-scenes | https://static.clubs.nfl.com/image/upload/w_1600,c_limit,q_90/jaguars/nnqfqugszfjislycexad.jpg | Caption: "A behind-the-scenes image from the 2025 Jaguars Team photoshoot on October 27, 2025." |
| KC | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/12 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3622.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Arrowhead Stadium |
| LAC | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=20251109 (venue 7065 SoFi Stadium; ESPN franchise venue is stale) | https://a.espncdn.com/i/venues/nfl/day/7065.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium SoFi Stadium |
| LAR | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard?dates=20251109 (venue 7065 SoFi Stadium; ESPN franchise venue is stale) | https://a.espncdn.com/i/venues/nfl/day/7065.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium SoFi Stadium |
| LV | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/13 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/6501.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Allegiant Stadium |
| MIA | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/15 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3948.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Hard Rock Stadium |
| MIN | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/16 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/interior/5239.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium U.S. Bank Stadium |
| NE | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/17 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3738.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Gillette Stadium |
| NO | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/18 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3493.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Caesars Superdome |
| NYG | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/19 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3839.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium MetLife Stadium |
| NYJ | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/20 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3839.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium MetLife Stadium |
| PHI | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/21 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3806.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Lincoln Financial Field |
| PIT | team_photo_day_2025 | 2025 | https://www.steelers.com/photos/photos-2025-team-photo-day | https://static.clubs.nfl.com/image/upload/w_1600,c_limit,q_90/steelers/vuypd1own6fpf3evvvcw.jpg | Caption: "The Pittsburgh Steelers pose for a photo during team photo day ... Sept. 13, 2025" (section of full-team pose) |
| SEA | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/26 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3673.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Lumen Field |
| SF | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/25 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/4738.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Levi's Stadium |
| TB | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/27 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3886.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Raymond James Stadium |
| TEN | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/10 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3810.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Nissan Stadium |
| WSH | stadium | — | https://site.api.espn.com/apis/site/v2/sports/football/nfl/teams/28 (franchise.venue) | https://a.espncdn.com/i/venues/nfl/day/3719.jpg | Fallback 2: no 2025 or recent full-team photo found; ESPN photo of home stadium Northwest Stadium |

## Wordmark fonts

Free SIL OFL fonts from google/fonts (licenses in `app/src/main/assets/font_licenses/`). They are approximate style lookalikes, **not** the clubs' proprietary typefaces.

| Font | Teams |
|---|---|
| Racing Sans One | ARI BUF DEN KC LAC SF |
| Russo One | ATL CAR JAX MIA NE SEA TEN |
| Playfair Display SC (Black) | BAL IND MIN NO PHI |
| Alfa Slab One | CHI HOU LV |
| Archivo Black | CIN DAL DET NYJ |
| Anton | CLE PIT (+ "THE LEAGUE") |
| Graduate | GB NYG WSH |
| Bebas Neue | LAR TB |

Name colors: fill = ESPN `color`, outline = ESPN `alternateColor` (swapped if the primary is near-black; white outline if both are dark).
