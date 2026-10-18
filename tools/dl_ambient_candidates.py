import urllib.request, os, sys
OUT = sys.argv[1]
C = {
"fire_414767":"https://cdn.freesound.org/previews/414/414767_4955305-hq.mp3",
"fire_370938":"https://cdn.freesound.org/previews/370/370938_6621799-hq.mp3",
"rain_723703":"https://cdn.freesound.org/previews/723/723703_11734604-hq.mp3",
"rain_234317":"https://cdn.freesound.org/previews/234/234317_3840537-hq.mp3",
"rain_518863":"https://cdn.freesound.org/previews/518/518863_3490256-hq.mp3",
"wind_697217":"https://cdn.freesound.org/previews/697/697217_7678208-hq.mp3",
"wind_531016":"https://cdn.freesound.org/previews/531/531016_9818404-hq.mp3",
"waves_531015":"https://cdn.freesound.org/previews/531/531015_9818404-hq.mp3",
"waves_376795":"https://cdn.freesound.org/previews/376/376795_6128004-hq.mp3",
"waves_456899":"https://cdn.freesound.org/previews/456/456899_9518146-hq.mp3",
"cafe_260062":"https://cdn.freesound.org/previews/260/260062_2258946-hq.mp3",
"cafe_421647":"https://cdn.freesound.org/previews/421/421647_8224400-hq.mp3",
"cafe_149334":"https://cdn.freesound.org/previews/149/149334_2078301-hq.mp3",
"rest_ssel":"https://archive.org/download/SSE_Library_AMBIENCE/RESTAURANT%20%26%20BAR/AMBRest_Restaurant%20background%20walla%20and%20dishes_CS_USC.mp3",
"rest_commons":"https://upload.wikimedia.org/wikipedia/commons/b/b5/Restaurant_ambience.ogg",
"mall":"https://upload.wikimedia.org/wikipedia/commons/d/d5/Shopping_mall_less_crowded.ogg",
"pool":"https://upload.wikimedia.org/wikipedia/commons/5/5b/Indoor_swimming_pool_hall.ogg",
"train_868524":"https://cdn.freesound.org/previews/868/868524_9639193-hq.mp3",
"station_193791":"https://cdn.freesound.org/previews/193/193791_71257-hq.mp3",
"fall_511075":"https://cdn.freesound.org/previews/511/511075_9250976-hq.mp3",
"fall_274259":"https://cdn.freesound.org/previews/274/274259_4486188-hq.mp3",
"stream_commons":"https://upload.wikimedia.org/wikipedia/commons/4/4f/Water_flowing_pouring_trickling.ogg",
"drip_177958":"https://cdn.freesound.org/previews/177/177958_985466-hq.mp3",
"drip_583804":"https://cdn.freesound.org/previews/583/583804_7841129-hq.mp3",
"cave_636110":"https://cdn.freesound.org/previews/636/636110_11705708-hq.mp3",
"forest_723913":"https://cdn.freesound.org/previews/723/723913_2008500-hq.mp3",
"forest_231537":"https://cdn.freesound.org/previews/231/231537_4034520-hq.mp3",
"forestnight_719558":"https://cdn.freesound.org/previews/719/719558_15600124-hq.mp3",
"cricket_175020":"https://cdn.freesound.org/previews/175/175020_2979997-hq.mp3",
"cricket_751465":"https://cdn.freesound.org/previews/751/751465_801011-hq.mp3",
"storm_704603":"https://cdn.freesound.org/previews/704/704603_4034520-hq.mp3",
"bowl_116315":"https://cdn.freesound.org/previews/116/116315_2061858-hq.mp3",
"bowl_449950":"https://cdn.freesound.org/previews/449/449950_1844073-hq.mp3",
"bowl_59152":"https://cdn.freesound.org/previews/59/59152_333271-hq.mp3",
"bowl_76888":"https://cdn.freesound.org/previews/76/76888_1051883-hq.mp3",
"bowl_commons":"https://upload.wikimedia.org/wikipedia/commons/e/e5/Singing_bowl.ogg",
"chimes_440929":"https://cdn.freesound.org/previews/440/440929_5426702-hq.mp3",
"ocean_archive":"https://archive.org/download/ocean-sea-sounds/Ocean%20Sound%20Nature%20Sounds.mp3",
}
for k,u in C.items():
    ext = u.rsplit(".",1)[1].split("?")[0]
    p = os.path.join(OUT, k+"."+ext)
    if os.path.exists(p): continue
    try:
        req = urllib.request.Request(u, headers={"User-Agent":"Mozilla/5.0 PulsePackBuilder"})
        d = urllib.request.urlopen(req, timeout=60).read()
        open(p,"wb").write(d); print("ok",k,len(d))
    except Exception as e: print("FAIL",k,e)
