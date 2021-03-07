# Timesrapse

Another one application for making photos with some given interval. Exciting. 

But! 
Allows to make photos in the background or even with screen locked on Android 8+ (Q tested).

Requires Android SDK 26. Uses AlarmManager and Camera2API.

```
┌──────────────┐    ┌──────────────┐
│              │    │              │
│ MainActivity ├────► AlarmManager │
│              │    │              │
└────────────┬─┘    └─┬────────────┘
             │        │
           start     capture
             │        │
             │        │
       ┌─────▼────────▼────┐
       │                   │
       │ ForegroundService │
       │                   │
       └─────────┬─────────┘
                 │
                 │
       ┌─────────▼─────────┐
       │                   │
       │    Camera2API     │
       │                   │
       └───────────────────┘
```

## Why?

All of the timelapse apps I have found on Google Play works like a standard camera app: some preview, some settings and some problems to work in the background. 

I planned to left an Android phone alone, without internet, sometimes without power supply, for period longer than 2 months. I don't care about _Instant gratification_ like [Microsoft Hyperlapse](https://play.google.com/store/apps/details?id=com.microsoft.hyperlapsemobile&hl=pl&gl=US) or about filters like in [Lapse It](https://play.google.com/store/apps/details?id=com.ui.LapseItPro&hl=pl&gl=US). In contrast, [Framelapse Pro](https://play.google.com/store/apps/details?id=com.neximolabs.droidtimelapsepro&hl=pl&gl=US) does not work in background on Android 8+.

## Beautiful, isn't it?

![No, it is not.](https://raw.githubusercontent.com/antrov/timesrapse-android/main/docs/Screenshot_1614447364.png)