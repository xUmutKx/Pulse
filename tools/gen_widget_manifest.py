"""Adds the themed widget receivers to the manifest, gives every widget receiver its own label and writes the provider XML files."""
import re, sys, os

ROOT = sys.argv[1]  # app/src/main
man_path = os.path.join(ROOT, 'AndroidManifest.xml')
man = open(man_path, encoding='utf-8').read()

# class -> (label, widthDp, heightDp, cellsW, cellsH)
SKINS = [
    ('W7CpuWidget', 'Windows 7 · CPU Meter', 180, 140, 3, 2), ('W7CalendarWidget', 'Windows 7 · Calendar', 110, 120, 2, 2),
    ('W7BatteryWidget', 'Windows 7 · Battery', 180, 110, 3, 2), ('W7DriveWidget', 'Windows 7 · Drive', 250, 70, 4, 1),
    ('W11ClockWidget', 'Windows 11 · Clock', 110, 110, 2, 2), ('W11DateWidget', 'Windows 11 · Date', 110, 110, 2, 2),
    ('W11CpuWidget', 'Windows 11 · CPU', 110, 110, 2, 2), ('W11MemoryWidget', 'Windows 11 · Memory', 110, 110, 2, 2),
    ('W11BatteryWidget', 'Windows 11 · Battery', 110, 110, 2, 2), ('W11StorageWidget', 'Windows 11 · Storage', 110, 110, 2, 2),
    ('W11CalendarWidget', 'Windows 11 · Calendar', 110, 110, 2, 2), ('W11TimeWidget', 'Windows 11 · Time', 110, 110, 2, 2),
    ('XpClockWidget', 'Windows XP · Clock', 110, 120, 2, 2), ('XpCpuWidget', 'Windows XP · CPU Usage', 110, 120, 2, 2),
    ('XpBatteryWidget', 'Windows XP · Power Meter', 180, 100, 3, 2), ('XpDiskWidget', 'Windows XP · Local Disk', 180, 120, 3, 2),
    ('W95TimeWidget', 'Windows 95 · Time', 110, 110, 2, 2), ('W95CpuWidget', 'Windows 95 · CPU', 180, 90, 3, 1),
    ('W95BatteryWidget', 'Windows 95 · Battery', 180, 90, 3, 1), ('W95MemoryWidget', 'Windows 95 · Memory', 180, 90, 3, 1),
    ('W95DateWidget', 'Windows 95 · Date', 180, 150, 3, 2),
    ('M3BatteryWidget', 'Material You · Battery', 110, 110, 2, 2), ('M3ClockWidget', 'Material You · Clock', 110, 110, 2, 2),
    ('M3DateWidget', 'Material You · Date', 110, 110, 2, 2), ('M3CpuWidget', 'Material You · CPU', 110, 110, 2, 2),
    ('M3StorageWidget', 'Material You · Storage', 180, 90, 3, 1), ('M3MemoryWidget', 'Material You · Memory', 110, 110, 2, 2),
    ('AmClockWidget', 'AMOLED · Clock', 180, 90, 3, 1), ('AmBatteryWidget', 'AMOLED · Battery', 110, 110, 2, 2),
    ('AmCpuWidget', 'AMOLED · CPU', 110, 110, 2, 2), ('AmDateWidget', 'AMOLED · Date', 110, 110, 2, 2),
    ('GlBatteryWidget', 'Glass · Battery', 110, 110, 2, 2), ('GlClockWidget', 'Glass · Clock', 110, 110, 2, 2),
    ('M3DatePillWidget', 'Material You · Date pill', 180, 90, 3, 1), ('M3ClockDateWidget', 'Material You · Clock and date', 180, 90, 3, 1),
    ('M3BatteryRingWidget', 'Material You · Battery ring', 110, 110, 2, 2), ('M3TempWidget', 'Material You · Temperature', 110, 110, 2, 2),
    ('M3RamBarWidget', 'Material You · Memory bar', 180, 90, 3, 1), ('M3NetworkWidget', 'Material You · Network', 180, 90, 3, 1),
    ('M3UptimeWidget', 'Material You · Uptime', 110, 110, 2, 2), ('M3OverviewWidget', 'Material You · Overview', 180, 90, 3, 1),
    ('GlTimeWidget', 'Glass · Digital clock', 180, 90, 3, 1), ('GlDateWidget', 'Glass · Date', 110, 110, 2, 2),
    ('GlBatteryRingWidget', 'Glass · Battery ring', 110, 110, 2, 2), ('GlCpuWidget', 'Glass · CPU', 110, 110, 2, 2),
    ('GlMemoryWidget', 'Glass · Memory', 110, 110, 2, 2), ('GlStorageWidget', 'Glass · Storage', 180, 90, 3, 1),
    ('IosClockDateWidget', 'iOS · Clock and date', 180, 90, 3, 1), ('IosRingsWidget', 'iOS · Battery rings', 110, 110, 2, 2),
    ('IosStorageWidget', 'iOS · Storage', 110, 110, 2, 2), ('IosMemoryWidget', 'iOS · Memory', 110, 110, 2, 2),
    ('IosUpNextWidget', 'iOS · Up next', 110, 110, 2, 2), ('IosWorldWidget', 'iOS · World clock', 250, 110, 4, 2),
]
OLD_LABELS = {
    'UsageWidget': 'Pulse · CPU and memory', 'BatteryWidget': 'Pulse · Battery', 'StorageWidget': 'Pulse · Storage', 'DownloadsWidget': 'Pulse · Downloads',
    'MemoryWidget': 'Pulse · Memory', 'TempWidget': 'Pulse · Temperature', 'NetworkWidget': 'Pulse · Network', 'ClockWidget': 'Pulse · Clock',
    'UptimeWidget': 'Pulse · Uptime', 'StorageBarWidget': 'Pulse · Storage bar', 'RamFreeWidget': 'Pulse · Free RAM', 'CoresWidget': 'Pulse · Cores',
    'BrightnessWidget': 'Pulse · Brightness', 'WifiWidget': 'Pulse · Wi-Fi', 'SignalWidget': 'Pulse · Signal', 'BatteryHealthWidget': 'Pulse · Battery health',
    'AppCountWidget': 'Pulse · Apps', 'LastDownloadWidget': 'Pulse · Last download', 'NextAlarmWidget': 'Pulse · Next alarm', 'QuickWidget': 'Pulse · Quick action',
    'Win7Clock1Widget': 'Windows 7 · Clock Traditional', 'Win7Clock2Widget': 'Windows 7 · Clock System', 'Win7Clock3Widget': 'Windows 7 · Clock Chronometer',
    'Win7Clock4Widget': 'Windows 7 · Clock Diner', 'Win7Clock5Widget': 'Windows 7 · Clock Flower', 'Win7Clock6Widget': 'Windows 7 · Clock Modern',
    'Win7Clock7Widget': 'Windows 7 · Clock Square', 'Win7Clock8Widget': 'Windows 7 · Clock Novelty', 'Win7Clock9Widget': 'Windows 7 · Clock Monitor',
    'Win7Clock10Widget': 'Pulse · Clock clear', 'Win7Clock11Widget': 'Pulse · Clock wood', 'IosClockWidget': 'iOS · Clock', 'IosDayWidget': 'iOS · Day',
    'IosCalendarWidget': 'iOS · Calendar', 'IosBatteryWidget': 'iOS · Battery', 'IosSunTimeWidget': 'iOS · Sun and time', 'Win11FrostClockWidget': 'Windows 11 · Frosted clock',
}

def snake(cls):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', cls.replace('Widget', '')).lower()

# labels on the receivers that are already there
def add_label(m):
    tag, cls = m.group(0), m.group(1)
    if 'android:label' in tag or cls not in OLD_LABELS: return tag
    return tag.replace('android:exported', 'android:label="%s" android:exported' % OLD_LABELS[cls], 1)
man = re.sub(r'<receiver android:name="com\.umutk\.pulse\.widgets\.(\w+)"[^>]*>', add_label, man)

# the new receivers, before </application>
block = ['        <!-- themed widgets (see widgets/ThemeWidgets.kt), one provider per look -->']
for cls, label, w, h, cw, ch in SKINS:
    if 'widgets.%s"' % cls in man: continue
    name = 'widget_skin_' + snake(cls)
    block.append('''        <receiver android:name="com.umutk.pulse.widgets.%s" android:label="%s" android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/%s" />
        </receiver>''' % (cls, label, name))
    xml = '''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="%ddp"
    android:minHeight="%ddp"
    android:targetCellWidth="%d"
    android:targetCellHeight="%d"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_face"
    android:previewImage="@drawable/preview_skin_%s"
    android:description="@string/app_name"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:widgetFeatures="reconfigurable"
    android:configure="com.umutk.pulse.widgets.WidgetConfigActivity" />
''' % (w, h, cw, ch, snake(cls))
    open(os.path.join(ROOT, 'res/xml', name + '.xml'), 'w', encoding='utf-8').write(xml)
if len(block) > 1:
    man = man.replace('    </application>', '\n'.join(block) + '\n    </application>', 1)
open(man_path, 'w', encoding='utf-8').write(man)
print('receivers with label:', man.count('android:label="Pulse ·') + man.count('android:label="Windows') + man.count('android:label="iOS') + man.count('android:label="Material') + man.count('android:label="AMOLED') + man.count('android:label="Glass'))
