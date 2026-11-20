package io.github.mio.autopickupisland;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Posts module status based on the latest Hook health reports. */
public final class ModuleStatusNotification {
    private static final String CHANNEL_ID = "module_hook_status";
    private static final int NOTIFICATION_ID = 0xA170;
    private static final String[] ROLES = {"loader", "voice", "aicr", "assist"};
    private static final String[] LABELS = {"系统装载", "超级小爱", "AICR", "AI 助手"};

    private ModuleStatusNotification() {}

    public static void update(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 33
                && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "模块加载状态", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示 AutoPickupIsland 最近收到的 Hook 状态");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        Map<String, String> reports = readReports(app);
        int loaded = reports.size();
        String title = loaded == 0 ? "AutoPickupIsland：等待 Hook"
                : loaded == ROLES.length ? "AutoPickupIsland：Hook 已接入"
                : "AutoPickupIsland：部分 Hook 已接入";
        String detail = loaded == 0 ? "尚未收到目标进程上报；打开相关系统功能后会自动更新"
                : "已收到 " + loaded + "/4 个进程的状态上报";
        StringBuilder expanded = new StringBuilder(detail);
        for (int i = 0; i < ROLES.length; i++) {
            expanded.append("\n").append(LABELS[i]).append("：")
                    .append(reports.getOrDefault(ROLES[i], "尚未上报"));
        }

        Intent launch = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launch == null) launch = new Intent(app, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(app, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(app, CHANNEL_ID) : new Notification.Builder(app);
        Notification notification = builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title).setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(expanded.toString()))
                .setContentIntent(pending).setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true).setOngoing(true).build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    public static void cancel(Context context) {
        if (context == null) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    private static Map<String, String> readReports(Context context) {
        Map<String, String> result = new LinkedHashMap<>();
        var prefs = context.getSharedPreferences("bridge_state", Context.MODE_PRIVATE);
        int boot = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        String modulePath = context.getApplicationInfo().sourceDir;
        for (String role : ROLES) {
            String raw = prefs.getString("hook_health_" + role, "");
            if (raw.isEmpty()) continue;
            try {
                JSONObject json = new JSONObject(raw);
                long at = json.optLong("at", 0L);
                boolean sameBoot = boot < 0 || json.optInt("boot", Integer.MIN_VALUE) == boot;
                boolean sameModule = modulePath.equals(json.optString("module_path", ""));
                boolean recent = at > 0L && System.currentTimeMillis() - at < 86400000L;
                if (sameBoot && sameModule && recent) result.put(role, "已收到状态上报");
            } catch (Exception ignored) { }
        }
        return result;
    }
}
