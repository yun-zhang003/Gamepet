# -*- coding: utf-8 -*-
"""独立静态校验：SkyPet 工程（不依赖 Android SDK）。只读。"""
import glob
import os
import re
import sys
import xml.dom.minidom as minidom

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
os.chdir(ROOT)

kt = sorted(glob.glob("SkyPet/app/src/main/java/com/skypet/pet/**/*.kt", recursive=True))
xmls = sorted(glob.glob("SkyPet/**/*.xml", recursive=True))
gradle = sorted(glob.glob("SkyPet/**/*.kts", recursive=True))

print("== counts ==")
print("kt_count =", len(kt))
print("xml_count =", len(xmls))
print("gradle_kts =", len(gradle))

print("\n== XML well-formed ==")
xml_err = []
for f in xmls:
    try:
        minidom.parse(f)
    except Exception as e:
        xml_err.append((f, str(e)))
print("xml_parse_errors =", xml_err if xml_err else "NONE")

print("\n== Kotlin brace balance ==")
brace_bad = []
for f in kt:
    s = open(f, encoding="utf-8").read()
    # strip line comments
    t = re.sub(r"//.*", "", s)
    # strip block comments
    t = re.sub(r"/\*.*?\*/", "", t, flags=re.S)
    # strip string literals (simple, handles escaped quotes)
    t = re.sub(r'"(?:\\.|[^"\\])*"', '""', t)
    # strip char literals
    t = re.sub(r"'(?:\\.|[^'\\])'", "''", t)
    if t.count("{") != t.count("}"):
        brace_bad.append((f, t.count("{"), t.count("}")))
print("brace_bad =", brace_bad if brace_bad else "NONE")

print("\n== double-escaped unicode literals ==")
bad_esc = []
for f in kt + gradle:
    s = open(f, encoding="utf-8").read()
    if "\\\\u" in s:
        bad_esc.append(f)
print("double_escaped =", bad_esc if bad_esc else "NONE")

print("\n== manifest ==")
mf = open("SkyPet/app/src/main/AndroidManifest.xml", encoding="utf-8").read()
for p in [
    "SYSTEM_ALERT_WINDOW",
    "FOREGROUND_SERVICE",
    "FOREGROUND_SERVICE_SPECIAL_USE",
    "POST_NOTIFICATIONS",
    "PACKAGE_USAGE_STATS",
]:
    print("perm %-32s %s" % (p, "YES" if p in mf else "MISSING"))
print("specialUse_attr      ", "YES" if 'android:foregroundServiceType="specialUse"' in mf else "MISSING")
print("FGS_SUBTYPE_prop     ", "YES" if "PROPERTY_SPECIAL_USE_FGS_SUBTYPE" in mf else "MISSING")
print("INTERNET_declared    ", "YES(bad)" if "android.permission.INTERNET" in mf else "NO(good)")
print("BOOT_receiver_perm   ", "YES(bad)" if "RECEIVE_BOOT_COMPLETED" in mf else "NO(good)")

print("\n== secrets ==")
key_pat = re.compile(r"sk-[A-Za-z0-9]{20,}|AIza[0-9A-Za-z_\-]{20,}|ghp_[A-Za-z0-9]{20,}")
hits = []
for f in kt + gradle:
    s = open(f, encoding="utf-8").read()
    if key_pat.search(s):
        hits.append(f)
print("secret_hits =", hits if hits else "NONE")

print("\n== presence of key invariants ==")
svc = open("SkyPet/app/src/main/java/com/skypet/pet/overlay/PetOverlayService.kt", encoding="utf-8").read()
checks = {
    "ReminderLogic object": "object ReminderLogic" in svc,
    "shouldFireReminder": "fun shouldFireReminder" in svc,
    "resting flag": "private var resting" in svc,
    "completeRest": "fun completeRest" in svc,
    "started=false on destroy": "prefs.started = false" in svc,
    "isPetVisibleNow": "fun isPetVisibleNow" in svc,
}
for k, v in checks.items():
    print("%-28s %s" % (k, "YES" if v else "MISSING"))

main = open("SkyPet/app/src/main/java/com/skypet/pet/MainActivity.kt", encoding="utf-8").read()
print("onResume register        ", "YES" if "onResume" in main and "registerStartedObserver()" in main else "MISSING")
print("onPause unregister       ", "YES" if "onPause" in main and "unregisterStartedObserver()" in main else "MISSING")

print("\n== result ==")
ok = not xml_err and not brace_bad and not bad_esc and not hits
print("STATIC_OK =", ok)
sys.exit(0 if ok else 1)
