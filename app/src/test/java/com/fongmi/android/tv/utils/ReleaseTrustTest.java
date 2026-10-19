package com.fongmi.android.tv.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Base64;

/**
 * 更新包可信来源判定单测（纯逻辑，无 Android 依赖，与 ReleaseSourceTest 同风格）。
 * <p>
 * 锁两件事：APK 字节只能从自己的域取；下载回来的签名字节必须是发布证书。
 * <p>
 * CERT_B64 是线上包（v5.6.85 / v5.6.87）APK Signing Block v2 signer 里那枚证书的 DER，
 * 用 keytool -printcert 读出 SHA-256 = PIN，与本仓库 ReleaseTrust.PINNED 一致。
 * 指纹在这里写成不透明常量而不是引用 PINNED：改 PINNED 时本文件不会跟着变，
 * realReleaseCertMatchesPin 就会失败，逼改动者重新核对线上包，而不是复制粘贴保持一致。
 */
public class ReleaseTrustTest {

    /** 官方发布证书 DER（base64，875 字节） */
    private static final String CERT_B64 = "MIIDZzCCAk+gAwIBAgIIWa3A+NFr85cwDQYJKoZIhvcNAQELBQAwYTELMAkGA1UEBhMCQ04xDjAM"
            + "BgNVBAgTBUdhbnN1MRAwDgYDVQQHEwdKaXVxdWFuMRAwDgYDVQQKEwdMYW5YaW5nMQwwCgYDVQQL"
            + "EwNEZXYxEDAOBgNVBAMTB0xhblhpbmcwIBcNMjYwOTE0MDE0NDU1WhgPMjA1NDAxMzAwMTQ0NTVa"
            + "MGExCzAJBgNVBAYTAkNOMQ4wDAYDVQQIEwVHYW5zdTEQMA4GA1UEBxMHSml1cXVhbjEQMA4GA1UE"
            + "ChMHTGFuWGluZzEMMAoGA1UECxMDRGV2MRAwDgYDVQQDEwdMYW5YaW5nMIIBIjANBgkqhkiG9w0B"
            + "AQEFAAOCAQ8AMIIBCgKCAQEAnRHbPHMSASZ3BmrBDJNqDJDnhYrD+zBq+20X4/9H0aSWXXwGN1WQ"
            + "H/3isdfrEeTBKayyNb7OQ7NefYD9EfKN4U2EULkeRToW/AIoj+tg27e8w4G7JaY2F3SxBAfasxpt"
            + "bjxo3RWCkRZTaBstcbdX8cbmLaG0EMSOKvOvP2rVlGpI1ZEtE023P+czeH3OOceiPu7WairGy4P9"
            + "6bPTITxYz9bnWsa80xBoPgF4K+3wXpUaRk81hH669wQC82Jr0UOI85LzuanX4monfSVnMd7WtQTN"
            + "6ymrZZlFd4/sXql6fkPXrdo2QYsMQSpXib3Fa6Xd58f7G8KJIT028Eb//8iEUwIDAQABoyEwHzAd"
            + "BgNVHQ4EFgQUGmRh1v3OGSzHY58Xl0GkYs7IpFgwDQYJKoZIhvcNAQELBQADggEBAJcYbbeUJK1N"
            + "ivYyir5+Njxbv9cMON2U8LG1RNuI/bbzApxuY2l0n4fYb0oecEbIugHpfvdEE2A5xEb8QHm4f6Om"
            + "Xrl7z2e9bc9t573MGmaLmDi93wK+QsV/wurMYKWXiNuCze3FAwqWDcgtVzRtKmEOfu/czeNybx8Y"
            + "UG4WJ2tlyrimeOnb4zEYqhOlEe/FI/mzrLZ8zdHJ/wopFj+26WoP5otb8vxi/Iii4fxCQP8riQZ3"
            + "E9GWdKHR5ssSxtKCV/bCF5ADSYdO3zdGRJMwoMf2TSK6xygs+XSbnQkFtPgiedkGJr+dtP+ry98y"
            + "1uySwCqsCluiYAT4Yt0Xfd5lCBQ=";

    private static final String PIN = "EB5CEF17FC2B5AEB67DA4EA960EE6A2BBBDF6F053B7B813566DDB29946B10135";

    private static byte[] cert() {
        return Base64.getDecoder().decode(CERT_B64);
    }

    /** 线上包的真实证书必须命中固定指纹 */
    @Test
    public void realReleaseCertMatchesPin() {
        assertTrue(ReleaseTrust.matches(cert(), PIN));
    }

    /** 生产用的 PINNED 非空且包含线上证书，否则真机会把每一次合法更新都拒掉 */
    @Test
    public void productionPinAcceptsRealCert() {
        assertTrue(ReleaseTrust.isTrustedCert(cert()));
    }

    /** 指纹比对对格式不敏感：小写、带冒号（keytool 输出形态）、带空白都要命中 */
    @Test
    public void pinComparisonIsFormatInsensitive() {
        String keytoolForm = PIN.replaceAll("(.{2})(?!$)", "$1:").toLowerCase();
        assertTrue(ReleaseTrust.matches(cert(), " " + keytoolForm + " "));
    }

    /** 轮换窗口：新旧两枚并存时任一命中即可信（只留一枚会永久锁死旧签名用户的更新） */
    @Test
    public void rotationWindowAcceptsEitherPin() {
        String other = "AA".repeat(32);
        assertTrue(ReleaseTrust.matches(cert(), other, PIN));
        assertTrue(ReleaseTrust.matches(cert(), PIN, other));
    }

    /** 异签名包必须拒绝 */
    @Test
    public void foreignCertIsRejected() {
        assertFalse(ReleaseTrust.matches("attacker-built cert".getBytes(), PIN));
    }

    /** 空指纹列表 / 空证书一律拒绝（fail closed）：漏配不能退化成「不校验」 */
    @Test
    public void missingInputRejects() {
        assertFalse(ReleaseTrust.matches(cert()));
        assertFalse(ReleaseTrust.matches(cert(), (String) null));
        assertFalse(ReleaseTrust.matches(null, PIN));
        assertFalse(ReleaseTrust.matches(new byte[0], PIN));
    }

    /** 归一化吸收冒号与大小写差异 */
    @Test
    public void normalizeHandlesKeytoolForm() {
        assertEquals(ReleaseTrust.normalize(PIN.substring(0, 8)), ReleaseTrust.normalize("eb:5C:ef:17"));
        assertEquals("", ReleaseTrust.normalize(null));
    }

    /** 正例：两源的 release 链接，以及实测 302 落点（精确主机名白名单会把这些全拦死） */
    @Test
    public void trustedHostsAreAccepted() {
        assertTrue(ReleaseTrust.isTrustedDownloadUrl("https://github.com/cyj265/lanxing-vod/releases/download/v5.6.87/lanxing-arm64_v8a-v5.6.87.apk"));
        assertTrue(ReleaseTrust.isTrustedDownloadUrl("https://gitee.com/cyj265/lanxing-vod/releases/download/v5.6.87/lanxing-arm64_v8a-v5.6.87.apk"));
        assertTrue(ReleaseTrust.isTrustedDownloadUrl("https://release-assets.githubusercontent.com/github-production-release-asset/1/x?sig=y"));
        assertTrue(ReleaseTrust.isTrustedDownloadUrl("https://objects.githubusercontent.com/whatever"));
        assertTrue(ReleaseTrust.isTrustedDownloadUrl("https://foruda.gitee.com/attach_file/179/a.apk?token=x"));
    }

    /**
     * 必须拒绝的形态。前两条是本次改动的核心：加速节点可以参与「版本发现」（只读一段 JSON），
     * 但不再承载 APK 字节——第三方站点既能替换包体，也能对单个用户反复返回当前版本做定向冻结更新，
     * 后者签名校验治不了，只能靠不让它递包。
     */
    @Test
    public void untrustedHostsAreRejected() {
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("https://gh-proxy.com/https://github.com/cyj265/lanxing-vod/a.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("https://ghfast.top/https://github.com/x/y.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("http://github.com/a/b.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("https://github.com.evil.com/a.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("https://github.comx/a.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("https://notgitee.com/a.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("https://evil.com/github.com/a.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("https://user:***@github.com/a.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("https://@github.com/a.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("https://github.com:443x/a.apk"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl("not a url"));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl(""));
        assertFalse(ReleaseTrust.isTrustedDownloadUrl(null));
    }
}
