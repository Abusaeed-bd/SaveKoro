# SaveKoro: Android ভিডিও ডাউনলোডার

Facebook, YouTube, Instagram, TikTok, Pinterest আর Threads থেকে ভিডিও বা MP3 ডাউনলোড করার Android অ্যাপ।
ইঞ্জিন: [yt-dlp](https://github.com/yt-dlp/yt-dlp) + ffmpeg ([youtubedl-android](https://github.com/yausername/youtubedl-android) লাইব্রেরির মাধ্যমে)।
কোনো সার্ভার লাগে না, ডাউনলোড হয় ফোন থেকে সরাসরি।

## 📥 ডাউনলোড করুন
**[⬇️ SaveKoro ডাউনলোড (বেশিরভাগ ফোন)](https://github.com/Abusaeed-bd/SaveKoro/releases/latest/download/SaveKoro-arm64.apk)**

পুরনো ফোন: [armv7](https://github.com/Abusaeed-bd/SaveKoro/releases/latest/download/SaveKoro-armv7.apk) · সব ফোন: [universal](https://github.com/Abusaeed-bd/SaveKoro/releases/latest/download/SaveKoro-universal.apk) · [সব ভার্সন](https://github.com/Abusaeed-bd/SaveKoro/releases)

## ফিচার
- লিংক পেস্ট করলে প্ল্যাটফর্ম নিজে চিনে নেয়
- অন্য অ্যাপ থেকে **Share → SaveKoro** করলে লিংক নিজে বসে যায় আর ভিডিওর তথ্য চলে আসে
- থাম্বনেইল, শিরোনাম, চ্যানেল, সময় দেখায়
- কোয়ালিটি বাছাই: সেরা / 1080p / 720p / 480p / MP3 অডিও
- লাইভ প্রগ্রেস, বাকি সময়, বাতিল করার বাটন
- ভিডিও যায় `Movies/SaveKoro`, অডিও যায় `Music/SaveKoro` (গ্যালারিতে দেখা যায়)
- দিনে একবার নিজে থেকে yt-dlp আপডেট হয়, চাইলে হাতেও আপডেট করা যায়
- Android 8.0+ (API 26) সাপোর্ট করে, ডার্ক মোড আর Material You আছে

## APK বানানোর সহজ উপায়: GitHub Actions (কম্পিউটারে কিছু ইনস্টল লাগবে না)
1. GitHub এ নতুন রিপো বানান (যেমন `SaveKoro`), তারপর এই ফোল্ডারের সব ফাইল আপলোড করে `main` ব্রাঞ্চে push করুন
2. রিপোর **Actions** ট্যাবে গেলে "Build APK" চলতে শুরু করবে (৫–১০ মিনিট লাগে)
3. শেষ হলে রানে ক্লিক করুন, নিচের **Artifacts** থেকে `SaveKoro-APK` ডাউনলোড করুন
4. zip খুললে কয়েকটা APK পাবেন:
   - `app-arm64-v8a-release.apk` → বেশিরভাগ নতুন ফোনের জন্য (সবচেয়ে ছোট)
   - `app-universal-release.apk` → সব ফোনে চলে (বড় সাইজ)

## Android Studio দিয়ে
ফোল্ডারটা Android Studio তে Open করুন → Sync → Run ▶️

## জানা সীমাবদ্ধতা
- **Instagram/Threads**: প্রাইভেট বা লগইন-লাগা পোস্ট এখন নামবে না। পরে লগইন-কুকি ফিচার যোগ করা যাবে।
- **Threads**: এটা yt-dlp এর সাপোর্টের ওপর নির্ভর করে, এখনও পরীক্ষা করা হয়নি। আগে টেস্ট করে দেখুন।
- **YouTube**: মাঝে মাঝে বট-চেক চায়। তখন "ইঞ্জিন আপডেট" চাপলে সাধারণত ঠিক হয়ে যায়।
- অ্যাপ বন্ধ করে দিলে চলতে থাকা ডাউনলোড থেমে যেতে পারে। পরের ধাপে notification সহ background service যোগ করা যাবে।
- yt-dlp + python + ffmpeg ভেতরে থাকে বলে APK এর সাইজ বড় হয়।

## বিক্রি বা পাবলিশ করার আগে
- Google Play সাধারণত YouTube ডাউনলোডার অ্যাপ নেয় না। APK সরাসরি দিন বা নিজের ওয়েবসাইট থেকে বিলি করুন, নয়তো Play ভার্সন থেকে YouTube বাদ দিন।
- `app/build.gradle.kts` এ release বিল্ড এখন debug key দিয়ে সাইন হয়। নিজের keystore বানিয়ে সেটা দিয়ে সাইন করুন।
- yt-dlp এর লাইসেন্স Unlicense আর youtubedl-android এর লাইসেন্স GPL-3.0। GPL এর কারণে অ্যাপ বিতরণ করলে সোর্স কোডও প্রকাশ করতে হতে পারে।
