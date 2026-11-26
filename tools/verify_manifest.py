import sys
import xml.etree.ElementTree as ET

android = "{http://schemas.android.com/apk/res/android}"
features = {
    "android.hardware.screen.landscape",
    "android.hardware.screen.portrait",
    "android.hardware.touchscreen",
}
root = ET.parse(sys.argv[1]).getroot()
assert root.get("package") == "com.oceanhunter.hunter"
assert root.get(android + "versionName") == "1.0.0"
assert root.get(android + "versionCode") == "1"
sdk = root.find("uses-sdk")
assert sdk is not None and sdk.get(android + "minSdkVersion") == "26"
assert sdk.get(android + "targetSdkVersion") == "36"
declarations = root.findall("uses-feature")
assert {d.get(android + "name") for d in declarations} == features
assert len(declarations) == len(features)
for declaration in declarations:
    assert declaration.get(android + "required") == "false"
assert not any(child.tag.startswith("uses-permission") for child in root)
assert not root.findall("permission")
app = root.find("application")
assert app is not None and app.get(android + "allowBackup") == "false"
assert app.find("activity").get(android + "screenOrientation") is None
print("Merged release manifest passed: package, version, API 26–36, three optional hardware features, zero permissions.")
