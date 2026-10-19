package com.fongmi.android.tv.utils;

import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 更新包的可信来源校验（纯逻辑，不依赖 Android，可在 JVM 单元测试里直接跑，与 ReleaseSource 同风格）。
 * <p>
 * 这里解决两件事，缺一不可：
 * <p>
 * 1) 签名字节校验：下载回来的 APK 必须由官方发布证书签署。Android 安装器本身也会拒绝异签名包
 * （INSTALL_FAILED_UPDATE_INCOMPATIBLE），但那意味着用户要自己撞上一个看不懂的失败码，
 * 再被搜索结果引导去「卸载重装」——而卸载重装会让同包名的假包以全新安装直接装成。
 * 所以在安装前就拒掉、并明确告知是来源异常，而不是留一个含糊的系统错误。
 * <p>
 * 2) 下载域白名单：APK 字节只从自己的域取。第三方 GitHub 加速站可以参与「版本发现」
 * （只读一段 JSON，拿不到包体），但不再作为 APK 的来源——它们既能替换内容，
 * 也能对单个用户反复返回当前版本实现定向冻结更新，后者签名校验治不了。
 */
public final class ReleaseTrust {

    /**
     * 官方发布证书的 SHA-256 指纹，等价于 keytool -printcert 的 SHA256 值（冒号可选）。
     * 取自线上包 v5.6.85 与 v5.6.87 的 APK v2 signer 块，两版一致，说明当前用户群共用同一把密钥。
     * <p>
     * 轮换 keystore 时：先把新指纹追加进来（两枚并存）发一版，再在下一版移除旧指纹。
     * 只留一枚会让装了旧签名包的用户永久收不到新签名更新（安装器拒绝覆盖安装）。
     * <p>
     * 这是公开信息（任何人都能从已发布的 APK 读出），不进 secrets：
     * 走环境变量的话，一旦 CI 里漏配就只能「构建失败」或「校验被跳过」二选一，
     * 后者正是本次要消灭的虚假安全感。轮换本来也该是一次被 review 的代码改动。
     */
    private static final String[] PINNED = {
            "EB5CEF17FC2B5AEB67DA4EA960EE6A2BBBDF6F053B7B813566DDB29946B10135"
    };

    /**
     * 允许承载 APK 字节的域（含其子域）。之所以按域而非精确主机名：
     * 两个源的下载链接都会 302 到 CDN，实测 v5.6.87 的落点分别是
     * release-assets.githubusercontent.com（GitHub）与 foruda.gitee.com（Gitee），
     * 精确主机名白名单会把每一次正常更新都拦死。
     * <p>
     * githubusercontent.com 一并覆盖 release-assets 与 objects 两类落点；
     * gh-proxy.com / ghfast.top 不在表内，这是有意的。
     */
    private static final String[] TRUSTED_DOMAINS = {"github.com", "githubusercontent.com", "gitee.com"};

    private ReleaseTrust() {
    }

    /**
     * APK 下载地址是否可信：强制 https + 主机落在可信域。
     * <p>
     * 用 URI 解析而不是手写正则：{@code https://github.com.evil.com/x} 这类后缀伪装、
     * 带 userinfo 的 {@code https://user:***@github.com/x}、畸形 host:port 都会在这里被正确判死。
     * 可信域走明文同样可被中间人改写，所以 http 一律拒绝。
     */
    public static boolean isTrustedDownloadUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return false;
            if (!"https".equalsIgnoreCase(scheme)) return false;
            if (uri.getUserInfo() != null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String domain : TRUSTED_DOMAINS) {
                if (host.equals(domain) || host.endsWith("." + domain)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** 证书 DER 的 SHA-256 是否命中任一固定指纹。列表为空时一律拒绝（fail closed）。 */
    public static boolean isTrustedCert(byte[] certDer) {
        return matches(certDer, PINNED);
    }

    /**
     * 指纹比对主体，与固定列表分开以便单测覆盖「格式不敏感 + 未配置即拒绝」。
     * pins 为空 → 拒绝：漏配不能退化成「不校验」，那正是本次要消灭的虚假安全感。
     */
    static boolean matches(byte[] certDer, String... pins) {
        if (certDer == null || certDer.length == 0 || pins == null || pins.length == 0) return false;
        String actual = sha256Hex(certDer);
        if (actual == null) return false;
        for (String pinned : pins) {
            if (normalize(pinned).equals(actual)) return true;
        }
        return false;
    }

    /** 归一化指纹：去冒号、去空白、转大写，使 keytool 输出与纯 hex 可互比。 */
    static String normalize(String value) {
        if (value == null) return "";
        return value.replace(":", "").trim().toUpperCase(Locale.ROOT);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte item : digest) sb.append(Character.forDigit((item >> 4) & 0xF, 16)).append(Character.forDigit(item & 0xF, 16));
            return sb.toString().toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
