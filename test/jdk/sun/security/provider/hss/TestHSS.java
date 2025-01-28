/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8298127 8347596
 * @library /test/lib
 * @summary tests for HSS/LMS provider
 * @modules java.base/sun.security.util
 * @run main TestHSS
 */

import java.io.*;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

import jdk.test.lib.Asserts;
import sun.security.util.*;

import jdk.test.lib.util.SerializationUtils;

public class TestHSS {
    static final String ALG = "HSS/LMS";
    static final String OID = "1.2.840.113549.1.9.16.3.17";

    public static void main(String[] args) throws Exception {

        // Tests 6-13 were generated with Bouncy Castle using parameter sets
        // mentioned in RFC 8554 section 6.4: two with W=8 and six with W=4.

        int i = 1;
        for (TestCase t : TestCases) {
            if (!kat(t)) {
                throw new RuntimeException("test case #" + i + " failed");
            }
            i++;
        }

        serializeTest();

        System.out.println("All tests passed");
    }

    static boolean kat(TestCase t) throws Exception {
        if (t.e == null) {
            if (verify(t.pk, t.sig, t.msg)) {
                return t.expected;
            } else {
                return !t.expected;
            }
        } else {
            // exception is expected
            try {
                verify(t.pk, t.sig, t.msg);
                return false;
            } catch (InvalidKeySpecException ex) {
                return t.e instanceof InvalidKeySpecException;
            } catch (SignatureException ex) {
                return t.e instanceof SignatureException;
            }
        }
    }

    static void serializeTest() throws Exception {
        final ObjectIdentifier oid;
        var pk = decode("""
                00000002
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b8
                50650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878
                """);

        // build x509 public key
        try {
            oid = ObjectIdentifier.of(OID);
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        // Encoding without inner OCTET STRING
        var pk0 = makeKey(oid, pk);
        // Encoding with inner OCTET STRING
        var pk1 = makeKey(oid, new DerOutputStream().putOctetString(pk).toByteArray());
        Asserts.assertEquals(pk0, pk1);

        PublicKey pk2 = (PublicKey) SerializationUtils
                .deserialize(SerializationUtils.serialize(pk1));
        Asserts.assertEquals(pk1, pk2);
    }

    static PublicKey makeKey(ObjectIdentifier oid, byte[] keyBits)
            throws Exception {
        var oidBytes = new DerOutputStream().write(DerValue.tag_Sequence,
                new DerOutputStream().putOID(oid));
        var x509encoding = new DerOutputStream().write(DerValue.tag_Sequence,
                oidBytes
                .putUnalignedBitString(new BitArray(keyBits.length * 8, keyBits)))
                .toByteArray();

        var x509KeySpec = new X509EncodedKeySpec(x509encoding);
        return KeyFactory.getInstance(ALG).generatePublic(x509KeySpec);
    }

    static boolean verify(byte[] pk, byte[] sig, byte[] msg) throws Exception {
        return verifyRawKey(pk, sig, msg) && verifyX509Key(pk, sig, msg);
    }

    static boolean verifyX509Key(byte[] pk, byte[] sig, byte[] msg)
            throws Exception {
        final ObjectIdentifier oid;

        // build x509 public key
        try {
            oid = ObjectIdentifier.of(OID);
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        var keyBits = new DerOutputStream().putOctetString(pk).toByteArray();
        var oidBytes = new DerOutputStream().write(DerValue.tag_Sequence,
                new DerOutputStream().putOID(oid));
        var x509encoding = new DerOutputStream().write(DerValue.tag_Sequence,
                oidBytes
                .putUnalignedBitString(new BitArray(keyBits.length * 8, keyBits)))
                .toByteArray();

        var x509KeySpec = new X509EncodedKeySpec(x509encoding);
        var pk1 = KeyFactory.getInstance(ALG).generatePublic(x509KeySpec);

        var v = Signature.getInstance(ALG);
        v.initVerify(pk1);
        v.update(msg);
        return v.verify(sig);
    }

    static boolean verifyRawKey(byte[] pk, byte[] sig, byte[] msg)
            throws Exception {
        var  provider = Security.getProvider("SUN");
        PublicKey pk1;

        // build public key
        RawKeySpec rks = new RawKeySpec(pk);
        KeyFactory kf = KeyFactory.getInstance(ALG, provider);
        pk1 = kf.generatePublic(rks);

        var v = Signature.getInstance(ALG);
        v.initVerify(pk1);
        v.update(msg);
        return v.verify(sig);
    }

    static byte[] decode(String s) {
        return HexFormat.of().parseHex(s
                        .replaceAll("//.*", "")
                        .replaceAll("\\s", ""));
    }

    record TestCase(
            Exception e,
            boolean expected,
            byte[] pk,
            byte[] msg,
            byte[] sig) {
    }

    static TestCase[] TestCases = new TestCase[] {
        // Test Case #1
        // RFC 8554 Test Case 1
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000002
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b850650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878"""),
            decode("""
                54686520706f77657273206e6f742064656c65676174656420746f2074686520
                556e69746564205374617465732062792074686520436f6e737469747574696f
                6e2c206e6f722070726f6869626974656420627920697420746f207468652053
                74617465732c2061726520726573657276656420746f20746865205374617465
                7320726573706563746976656c792c206f7220746f207468652070656f706c65
                2e0a"""),
            decode("""
                00000001
                00000005
                00000004
                d32b56671d7eb98833c49b433c272586bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb69128ec45146f86292f9dff9610a7bf95
                a64c7f60f6261a62043f86c70324b7707f5b4a8a6e19c114c7be866d488778a0
                e05fd5c6509a6e61d559cf1a77a970de927d60c70d3de31a7fa0100994e162a2
                582e8ff1b10cd99d4e8e413ef469559f7d7ed12c838342f9b9c96b83a4943d16
                81d84b15357ff48ca579f19f5e71f18466f2bbef4bf660c2518eb20de2f66e3b
                14784269d7d876f5d35d3fbfc7039a462c716bb9f6891a7f41ad133e9e1f6d95
                60b960e7777c52f060492f2d7c660e1471e07e72655562035abc9a701b473ecb
                c3943c6b9c4f2405a3cb8bf8a691ca51d3f6ad2f428bab6f3a30f55dd9625563
                f0a75ee390e385e3ae0b906961ecf41ae073a0590c2eb6204f44831c26dd768c
                35b167b28ce8dc988a3748255230cef99ebf14e730632f27414489808afab1d1
                e783ed04516de012498682212b07810579b250365941bcc98142da13609e9768
                aaf65de7620dabec29eb82a17fde35af15ad238c73f81bdb8dec2fc0e7f93270
                1099762b37f43c4a3c20010a3d72e2f606be108d310e639f09ce7286800d9ef8
                a1a40281cc5a7ea98d2adc7c7400c2fe5a101552df4e3cccfd0cbf2ddf5dc677
                9cbbc68fee0c3efe4ec22b83a2caa3e48e0809a0a750b73ccdcf3c79e6580c15
                4f8a58f7f24335eec5c5eb5e0cf01dcf4439424095fceb077f66ded5bec73b27
                c5b9f64a2a9af2f07c05e99e5cf80f00252e39db32f6c19674f190c9fbc506d8
                26857713afd2ca6bb85cd8c107347552f30575a5417816ab4db3f603f2df56fb
                c413e7d0acd8bdd81352b2471fc1bc4f1ef296fea1220403466b1afe78b94f7e
                cf7cc62fb92be14f18c2192384ebceaf8801afdf947f698ce9c6ceb696ed70e9
                e87b0144417e8d7baf25eb5f70f09f016fc925b4db048ab8d8cb2a661ce3b57a
                da67571f5dd546fc22cb1f97e0ebd1a65926b1234fd04f171cf469c76b884cf3
                115cce6f792cc84e36da58960c5f1d760f32c12faef477e94c92eb75625b6a37
                1efc72d60ca5e908b3a7dd69fef0249150e3eebdfed39cbdc3ce9704882a2072
                c75e13527b7a581a556168783dc1e97545e31865ddc46b3c957835da252bb732
                8d3ee2062445dfb85ef8c35f8e1f3371af34023cef626e0af1e0bc017351aae2
                ab8f5c612ead0b729a1d059d02bfe18efa971b7300e882360a93b025ff97e9e0
                eec0f3f3f13039a17f88b0cf808f488431606cb13f9241f40f44e537d302c64a
                4f1f4ab949b9feefadcb71ab50ef27d6d6ca8510f150c85fb525bf25703df720
                9b6066f09c37280d59128d2f0f637c7d7d7fad4ed1c1ea04e628d221e3d8db77
                b7c878c9411cafc5071a34a00f4cf07738912753dfce48f07576f0d4f94f42c6
                d76f7ce973e9367095ba7e9a3649b7f461d9f9ac1332a4d1044c96aefee67676
                401b64457c54d65fef6500c59cdfb69af7b6dddfcb0f086278dd8ad0686078df
                b0f3f79cd893d314168648499898fbc0ced5f95b74e8ff14d735cdea968bee74
                00000005
                d8b8112f9200a5e50c4a262165bd342cd800b8496810bc716277435ac376728d
                129ac6eda839a6f357b5a04387c5ce97382a78f2a4372917eefcbf93f63bb591
                12f5dbe400bd49e4501e859f885bf0736e90a509b30a26bfac8c17b5991c157e
                b5971115aa39efd8d564a6b90282c3168af2d30ef89d51bf14654510a12b8a14
                4cca1848cf7da59cc2b3d9d0692dd2a20ba3863480e25b1b85ee860c62bf5136
                00000005
                00000004
                d2f14ff6346af964569f7d6cb880a1b66c5004917da6eafe4d9ef6c6407b3db0
                e5485b122d9ebe15cda93cfec582d7ab
                0000000a
                00000004
                0703c491e7558b35011ece3592eaa5da4d918786771233e8353bc4f62323185c
                95cae05b899e35dffd717054706209988ebfdf6e37960bb5c38d7657e8bffeef
                9bc042da4b4525650485c66d0ce19b317587c6ba4bffcc428e25d08931e72dfb
                6a120c5612344258b85efdb7db1db9e1865a73caf96557eb39ed3e3f426933ac
                9eeddb03a1d2374af7bf77185577456237f9de2d60113c23f846df26fa942008
                a698994c0827d90e86d43e0df7f4bfcdb09b86a373b98288b7094ad81a0185ac
                100e4f2c5fc38c003c1ab6fea479eb2f5ebe48f584d7159b8ada03586e65ad9c
                969f6aecbfe44cf356888a7b15a3ff074f771760b26f9c04884ee1faa329fbf4
                e61af23aee7fa5d4d9a5dfcf43c4c26ce8aea2ce8a2990d7ba7b57108b47dabf
                beadb2b25b3cacc1ac0cef346cbb90fb044beee4fac2603a442bdf7e507243b7
                319c9944b1586e899d431c7f91bcccc8690dbf59b28386b2315f3d36ef2eaa3c
                f30b2b51f48b71b003dfb08249484201043f65f5a3ef6bbd61ddfee81aca9ce6
                0081262a00000480dcbc9a3da6fbef5c1c0a55e48a0e729f9184fcb1407c3152
                9db268f6fe50032a363c9801306837fafabdf957fd97eafc80dbd165e435d0e2
                dfd836a28b354023924b6fb7e48bc0b3ed95eea64c2d402f4d734c8dc26f3ac5
                91825daef01eae3c38e3328d00a77dc657034f287ccb0f0e1c9a7cbdc828f627
                205e4737b84b58376551d44c12c3c215c812a0970789c83de51d6ad787271963
                327f0a5fbb6b5907dec02c9a90934af5a1c63b72c82653605d1dcce51596b3c2
                b45696689f2eb382007497557692caac4d57b5de9f5569bc2ad0137fd47fb47e
                664fcb6db4971f5b3e07aceda9ac130e9f38182de994cff192ec0e82fd6d4cb7
                f3fe00812589b7a7ce515440456433016b84a59bec6619a1c6c0b37dd1450ed4
                f2d8b584410ceda8025f5d2d8dd0d2176fc1cf2cc06fa8c82bed4d944e71339e
                ce780fd025bd41ec34ebff9d4270a3224e019fcb444474d482fd2dbe75efb203
                89cc10cd600abb54c47ede93e08c114edb04117d714dc1d525e11bed8756192f
                929d15462b939ff3f52f2252da2ed64d8fae88818b1efa2c7b08c8794fb1b214
                aa233db3162833141ea4383f1a6f120be1db82ce3630b3429114463157a64e91
                234d475e2f79cbf05e4db6a9407d72c6bff7d1198b5c4d6aad2831db61274993
                715a0182c7dc8089e32c8531deed4f7431c07c02195eba2ef91efb5613c37af7
                ae0c066babc69369700e1dd26eddc0d216c781d56e4ce47e3303fa73007ff7b9
                49ef23be2aa4dbf25206fe45c20dd888395b2526391a724996a44156beac8082
                12858792bf8e74cba49dee5e8812e019da87454bff9e847ed83db07af3137430
                82f880a278f682c2bd0ad6887cb59f652e155987d61bbf6a88d36ee93b6072e6
                656d9ccbaae3d655852e38deb3a2dcf8058dc9fb6f2ab3d3b3539eb77b248a66
                1091d05eb6e2f297774fe6053598457cc61908318de4b826f0fc86d4bb117d33
                e865aa805009cc2918d9c2f840c4da43a703ad9f5b5806163d7161696b5a0adc
                00000005
                d5c0d1bebb06048ed6fe2ef2c6cef305b3ed633941ebc8b3bec9738754cddd60
                e1920ada52f43d055b5031cee6192520d6a5115514851ce7fd448d4a39fae2ab
                2335b525f484e9b40d6a4a969394843bdcf6d14c48e8015e08ab92662c05c6e9
                f90b65a7a6201689999f32bfd368e5e3ec9cb70ac7b8399003f175c40885081a
                09ab3034911fe125631051df0408b3946b0bde790911e8978ba07dd56c73e7ee
                """)
        ),

        // Test Case #2
        // RFC 8554 Test Case 2
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000002
                00000006
                00000003
                d08fabd4a2091ff0a8cb4ed834e7453432a58885cd9ba0431235466bff9651c6
                c92124404d45fa53cf161c28f1ad5a8e"""),
            decode("""
                54686520656e756d65726174696f6e20696e2074686520436f6e737469747574
                696f6e2c206f66206365727461696e207269676874732c207368616c6c206e6f
                7420626520636f6e73747275656420746f2064656e79206f7220646973706172
                616765206f74686572732072657461696e6564206279207468652070656f706c
                652e0a"""),
            decode("""
                00000001
                00000003
                00000003
                3d46bee8660f8f215d3f96408a7a64cf1c4da02b63a55f62c666ef5707a914ce
                0674e8cb7a55f0c48d484f31f3aa4af9719a74f22cf823b94431d01c926e2a76
                bb71226d279700ec81c9e95fb11a0d10d065279a5796e265ae17737c44eb8c59
                4508e126a9a7870bf4360820bdeb9a01d9693779e416828e75bddd7d8c70d50a
                0ac8ba39810909d445f44cb5bb58de737e60cb4345302786ef2c6b14af212ca1
                9edeaa3bfcfe8baa6621ce88480df2371dd37add732c9de4ea2ce0dffa53c926
                49a18d39a50788f4652987f226a1d48168205df6ae7c58e049a25d4907edc1aa
                90da8aa5e5f7671773e941d8055360215c6b60dd35463cf2240a9c06d694e9cb
                54e7b1e1bf494d0d1a28c0d31acc75161f4f485dfd3cb9578e836ec2dc722f37
                ed30872e07f2b8bd0374eb57d22c614e09150f6c0d8774a39a6e168211035dc5
                2988ab46eaca9ec597fb18b4936e66ef2f0df26e8d1e34da28cbb3af75231372
                0c7b345434f72d65314328bbb030d0f0f6d5e47b28ea91008fb11b05017705a8
                be3b2adb83c60a54f9d1d1b2f476f9e393eb5695203d2ba6ad815e6a111ea293
                dcc21033f9453d49c8e5a6387f588b1ea4f706217c151e05f55a6eb7997be09d
                56a326a32f9cba1fbe1c07bb49fa04cecf9df1a1b815483c75d7a27cc88ad1b1
                238e5ea986b53e087045723ce16187eda22e33b2c70709e53251025abde89396
                45fc8c0693e97763928f00b2e3c75af3942d8ddaee81b59a6f1f67efda0ef81d
                11873b59137f67800b35e81b01563d187c4a1575a1acb92d087b517a8833383f
                05d357ef4678de0c57ff9f1b2da61dfde5d88318bcdde4d9061cc75c2de3cd47
                40dd7739ca3ef66f1930026f47d9ebaa713b07176f76f953e1c2e7f8f271a6ca
                375dbfb83d719b1635a7d8a13891957944b1c29bb101913e166e11bd5f34186f
                a6c0a555c9026b256a6860f4866bd6d0b5bf90627086c6149133f8282ce6c9b3
                622442443d5eca959d6c14ca8389d12c4068b503e4e3c39b635bea245d9d05a2
                558f249c9661c0427d2e489ca5b5dde220a90333f4862aec793223c781997da9
                8266c12c50ea28b2c438e7a379eb106eca0c7fd6006e9bf612f3ea0a454ba3bd
                b76e8027992e60de01e9094fddeb3349883914fb17a9621ab929d970d101e45f
                8278c14b032bcab02bd15692d21b6c5c204abbf077d465553bd6eda645e6c306
                5d33b10d518a61e15ed0f092c32226281a29c8a0f50cde0a8c66236e29c2f310
                a375cebda1dc6bb9a1a01dae6c7aba8ebedc6371a7d52aacb955f83bd6e4f84d
                2949dcc198fb77c7e5cdf6040b0f84faf82808bf985577f0a2acf2ec7ed7c0b0
                ae8a270e951743ff23e0b2dd12e9c3c828fb5598a22461af94d568f29240ba28
                20c4591f71c088f96e095dd98beae456579ebbba36f6d9ca2613d1c26eee4d8c
                73217ac5962b5f3147b492e8831597fd89b64aa7fde82e1974d2f6779504dc21
                435eb3109350756b9fdabe1c6f368081bd40b27ebcb9819a75d7df8bb07bb05d
                b1bab705a4b7e37125186339464ad8faaa4f052cc1272919fde3e025bb64aa8e
                0eb1fcbfcc25acb5f718ce4f7c2182fb393a1814b0e942490e52d3bca817b2b2
                6e90d4c9b0cc38608a6cef5eb153af0858acc867c9922aed43bb67d7b33acc51
                9313d28d41a5c6fe6cf3595dd5ee63f0a4c4065a083590b275788bee7ad875a7
                f88dd73720708c6c6c0ecf1f43bbaadae6f208557fdc07bd4ed91f88ce4c0de8
                42761c70c186bfdafafc444834bd3418be4253a71eaf41d718753ad07754ca3e
                ffd5960b0336981795721426803599ed5b2b7516920efcbe32ada4bcf6c73bd2
                9e3fa152d9adeca36020fdeeee1b739521d3ea8c0da497003df1513897b0f547
                94a873670b8d93bcca2ae47e64424b7423e1f078d9554bb5232cc6de8aae9b83
                fa5b9510beb39ccf4b4e1d9c0f19d5e17f58e5b8705d9a6837a7d9bf99cd1338
                7af256a8491671f1f2f22af253bcff54b673199bdb7d05d81064ef05f80f0153
                d0be7919684b23da8d42ff3effdb7ca0985033f389181f47659138003d712b5e
                c0a614d31cc7487f52de8664916af79c98456b2c94a8038083db55391e347586
                2250274a1de2584fec975fb09536792cfbfcf6192856cc76eb5b13dc4709e2f7
                301ddff26ec1b23de2d188c999166c74e1e14bbc15f457cf4e471ae13dcbdd9c
                50f4d646fc6278e8fe7eb6cb5c94100fa870187380b777ed19d7868fd8ca7ceb
                7fa7d5cc861c5bdac98e7495eb0a2ceec1924ae979f44c5390ebedddc65d6ec1
                1287d978b8df064219bc5679f7d7b264a76ff272b2ac9f2f7cfc9fdcfb6a5142
                8240027afd9d52a79b647c90c2709e060ed70f87299dd798d68f4fadd3da6c51
                d839f851f98f67840b964ebe73f8cec41572538ec6bc131034ca2894eb736b3b
                da93d9f5f6fa6f6c0f03ce43362b8414940355fb54d3dfdd03633ae108f3de3e
                bc85a3ff51efeea3bc2cf27e1658f1789ee612c83d0f5fd56f7cd071930e2946
                beeecaa04dccea9f97786001475e0294bc2852f62eb5d39bb9fbeef75916efe4
                4a662ecae37ede27e9d6eadfdeb8f8b2b2dbccbf96fa6dbaf7321fb0e701f4d4
                29c2f4dcd153a2742574126e5eaccc77686acf6e3ee48f423766e0fc466810a9
                05ff5453ec99897b56bc55dd49b991142f65043f2d744eeb935ba7f4ef23cf80
                cc5a8a335d3619d781e7454826df720eec82e06034c44699b5f0c44a8787752e
                057fa3419b5bb0e25d30981e41cb1361322dba8f69931cf42fad3f3bce6ded5b
                8bfc3d20a2148861b2afc14562ddd27f12897abf0685288dcc5c4982f8260268
                46a24bf77e383c7aacab1ab692b29ed8c018a65f3dc2b87ff619a633c41b4fad
                b1c78725c1f8f922f6009787b1964247df0136b1bc614ab575c59a16d089917b
                d4a8b6f04d95c581279a139be09fcf6e98a470a0bceca191fce476f9370021cb
                c05518a7efd35d89d8577c990a5e19961ba16203c959c91829ba7497cffcbb4b
                294546454fa5388a23a22e805a5ca35f956598848bda678615fec28afd5da61a
                00000006
                b326493313053ced3876db9d237148181b7173bc7d042cefb4dbe94d2e58cd21
                a769db4657a103279ba8ef3a629ca84ee836172a9c50e51f45581741cf808315
                0b491cb4ecbbabec128e7c81a46e62a67b57640a0a78be1cbf7dd9d419a10cd8
                686d16621a80816bfdb5bdc56211d72ca70b81f1117d129529a7570cf79cf52a
                7028a48538ecdd3b38d3d5d62d26246595c4fb73a525a5ed2c30524ebb1d8cc8
                2e0c19bc4977c6898ff95fd3d310b0bae71696cef93c6a552456bf96e9d075e3
                83bb7543c675842bafbfc7cdb88483b3276c29d4f0a341c2d406e40d4653b7e4
                d045851acf6a0a0ea9c710b805cced4635ee8c107362f0fc8d80c14d0ac49c51
                6703d26d14752f34c1c0d2c4247581c18c2cf4de48e9ce949be7c888e9caebe4
                a415e291fd107d21dc1f084b1158208249f28f4f7c7e931ba7b3bd0d824a4570
                00000005
                00000004
                215f83b7ccb9acbcd08db97b0d04dc2ba1cd035833e0e90059603f26e07ad2aa
                d152338e7a5e5984bcd5f7bb4eba40b7
                00000004
                00000004
                0eb1ed54a2460d512388cad533138d240534e97b1e82d33bd927d201dfc24ebb
                11b3649023696f85150b189e50c00e98850ac343a77b3638319c347d7310269d
                3b7714fa406b8c35b021d54d4fdada7b9ce5d4ba5b06719e72aaf58c5aae7aca
                057aa0e2e74e7dcfd17a0823429db62965b7d563c57b4cec942cc865e29c1dad
                83cac8b4d61aacc457f336e6a10b66323f5887bf3523dfcadee158503bfaa89d
                c6bf59daa82afd2b5ebb2a9ca6572a6067cee7c327e9039b3b6ea6a1edc7fdc3
                df927aade10c1c9f2d5ff446450d2a3998d0f9f6202b5e07c3f97d2458c69d3c
                8190643978d7a7f4d64e97e3f1c4a08a7c5bc03fd55682c017e2907eab07e5bb
                2f190143475a6043d5e6d5263471f4eecf6e2575fbc6ff37edfa249d6cda1a09
                f797fd5a3cd53a066700f45863f04b6c8a58cfd341241e002d0d2c0217472bf1
                8b636ae547c1771368d9f317835c9b0ef430b3df4034f6af00d0da44f4af7800
                bc7a5cf8a5abdb12dc718b559b74cab9090e33cc58a955300981c420c4da8ffd
                67df540890a062fe40dba8b2c1c548ced22473219c534911d48ccaabfb71bc71
                862f4a24ebd376d288fd4e6fb06ed8705787c5fedc813cd2697e5b1aac1ced45
                767b14ce88409eaebb601a93559aae893e143d1c395bc326da821d79a9ed41dc
                fbe549147f71c092f4f3ac522b5cc57290706650487bae9bb5671ecc9ccc2ce5
                1ead87ac01985268521222fb9057df7ed41810b5ef0d4f7cc67368c90f573b1a
                c2ce956c365ed38e893ce7b2fae15d3685a3df2fa3d4cc098fa57dd60d2c9754
                a8ade980ad0f93f6787075c3f680a2ba1936a8c61d1af52ab7e21f416be09d2a
                8d64c3d3d8582968c2839902229f85aee297e717c094c8df4a23bb5db658dd37
                7bf0f4ff3ffd8fba5e383a48574802ed545bbe7a6b4753533353d73706067640
                135a7ce517279cd683039747d218647c86e097b0daa2872d54b8f3e508598762
                9547b830d8118161b65079fe7bc59a99e9c3c7380e3e70b7138fe5d9be255150
                2b698d09ae193972f27d40f38dea264a0126e637d74ae4c92a6249fa103436d3
                eb0d4029ac712bfc7a5eacbdd7518d6d4fe903a5ae65527cd65bb0d4e9925ca2
                4fd7214dc617c150544e423f450c99ce51ac8005d33acd74f1bed3b17b7266a4
                a3bb86da7eba80b101e15cb79de9a207852cf91249ef480619ff2af8cabca831
                25d1faa94cbb0a03a906f683b3f47a97c871fd513e510a7a25f283b196075778
                496152a91c2bf9da76ebe089f4654877f2d586ae7149c406e663eadeb2b5c7e8
                2429b9e8cb4834c83464f079995332e4b3c8f5a72bb4b8c6f74b0d45dc6c1f79
                952c0b7420df525e37c15377b5f0984319c3993921e5ccd97e097592064530d3
                3de3afad5733cbe7703c5296263f77342efbf5a04755b0b3c997c4328463e84c
                aa2de3ffdcd297baaaacd7ae646e44b5c0f16044df38fabd296a47b3a838a913
                982fb2e370c078edb042c84db34ce36b46ccb76460a690cc86c302457dd1cde1
                97ec8075e82b393d542075134e2a17ee70a5e187075d03ae3c853cff60729ba4
                00000005
                4de1f6965bdabc676c5a4dc7c35f97f82cb0e31c68d04f1dad96314ff09e6b3d
                e96aeee300d1f68bf1bca9fc58e4032336cd819aaf578744e50d1357a0e42867
                04d341aa0a337b19fe4bc43c2e79964d4f351089f2e0e41c7c43ae0d49e7f404
                b0f75be80ea3af098c9752420a8ac0ea2bbb1f4eeba05238aef0d8ce63f0c6e5
                e4041d95398a6f7f3e0ee97cc1591849d4ed236338b147abde9f51ef9fd4e1c1
                """)
        ),

        // Test Case #3
        // Additional Parameter sets for LMS Hash-Based Signatures (fluhrer)
        // This test should fail because SHA256_M24 is supported.
        new TestCase(
            new InvalidKeySpecException(),
            false, // expected result
            decode("""
                00000001
                0000000a
                00000008
                202122232425262728292a2b2c2d2e2f2c571450aed99cfb4f4ac285da148827
                96618314508b12d2"""),
            decode("""
                54657374206d65737361676520666f72205348413235362d3139320a"""),
            decode("""
                00000000
                00000005
                00000008
                0b5040a18c1b5cabcbc85b047402ec6294a30dd8da8fc3dae13b9f0875f09361
                dc77fcc4481ea463c073716249719193614b835b4694c059f12d3aedd34f3db9
                3f3580fb88743b8b3d0648c0537b7a50e433d7ea9d6672fffc5f42770feab4f9
                8eb3f3b23fd2061e4d0b38f832860ae76673ad1a1a52a9005dcf1bfb56fe16ff
                723627612f9a48f790f3c47a67f870b81e919d99919c8db48168838cece0abfb
                683da48b9209868be8ec10c63d8bf80d36498dfc205dc45d0dd870572d6d8f1d
                90177cf5137b8bbf7bcb67a46f86f26cfa5a44cbcaa4e18da099a98b0b3f96d5
                ac8ac375d8da2a7c248004ba11d7ac775b9218359cddab4cf8ccc6d54cb7e1b3
                5a36ddc9265c087063d2fc6742a7177876476a324b03295bfed99f2eaf1f3897
                0583c1b2b616aad0f31cd7a4b1bb0a51e477e94a01bbb4d6f8866e2528a159df
                3d6ce244d2b6518d1f0212285a3c2d4a927054a1e1620b5b02aab0c8c10ed48a
                e518ea73cba81fcfff88bff461dac51e7ab4ca75f47a6259d24820b9995792d1
                39f61ae2a8186ae4e3c9bfe0af2cc717f424f41aa67f03faedb0665115f2067a
                46843a4cbbd297d5e83bc1aafc18d1d03b3d894e8595a6526073f02ab0f08b99
                fd9eb208b59ff6317e5545e6f9ad5f9c183abd043d5acd6eb2dd4da3f02dbc31
                67b468720a4b8b92ddfe7960998bb7a0ecf2a26a37598299413f7b2aecd39a30
                cec527b4d9710c4473639022451f50d01c0457125da0fa4429c07dad859c846c
                bbd93ab5b91b01bc770b089cfede6f651e86dd7c15989c8b5321dea9ca608c71
                fd862323072b827cee7a7e28e4e2b999647233c3456944bb7aef9187c96b3f5b
                79fb98bc76c3574dd06f0e95685e5b3aef3a54c4155fe3ad817749629c30adbe
                897c4f4454c86c49
                0000000a
                e9ca10eaa811b22ae07fb195e3590a334ea64209942fbae338d19f152182c807
                d3c40b189d3fcbea942f44682439b191332d33ae0b761a2a8f984b56b2ac2fd4
                ab08223a69ed1f7719c7aa7e9eee96504b0e60c6bb5c942d695f0493eb25f80a
                5871cffd131d0e04ffe5065bc7875e82d34b40b69dd9f3c1""")
        ),

        // Test Case #4
        // Additional Parameter sets for LMS Hash-Based Signatures (fluhrer)
        // This test should fail because SHAKE is not supported.
        new TestCase(
            new InvalidKeySpecException(),
            false, // expected result
            decode("""
                00000001
                00000014
                00000010
                505152535455565758595a5b5c5d5e5fdb54a4509901051c01e26d9990e55034
                7986da87924ff0b1"""),
            decode("""
                54657374206d65737361676520666f72205348414b453235362d3139320a
                """),
            decode("""
                00000000
                00000006
                00000010
                84219da9ce9fffb16edb94527c6d10565587db28062deac4208e62fc4fbe9d85
                deb3c6bd2c01640accb387d8a6093d68511234a6a1a50108091c034cb1777e02
                b5df466149a66969a498e4200c0a0c1bf5d100cdb97d2dd40efd3cada278acc5
                a570071a043956112c6deebd1eb3a7b56f5f6791515a7b5ffddb0ec2d9094bfb
                c889ea15c3c7b9bea953efb75ed648f535b9acab66a2e9631e426e4e99b733ca
                a6c55963929b77fec54a7e703d8162e736875cb6a455d4a9015c7a6d8fd5fe75
                e402b47036dc3770f4a1dd0a559cb478c7fb1726005321be9d1ac2de94d731ee
                4ca79cff454c811f46d11980909f047b2005e84b6e15378446b1ca691efe491e
                a98acc9d3c0f785caba5e2eb3c306811c240ba22802923827d582639304a1e97
                83ba5bc9d69d999a7db8f749770c3c04a152856dc726d8067921465b61b3f847
                b13b2635a45379e5adc6ff58a99b00e60ac767f7f30175f9f7a140257e218be3
                07954b1250c9b41902c4fa7c90d8a592945c66e86a76defcb84500b55598a199
                0faaa10077c74c94895731585c8f900de1a1c675bd8b0c180ebe2b5eb3ef8019
                ece3e1ea7223eb7906a2042b6262b4aa25c4b8a05f205c8befeef11ceff12825
                08d71bc2a8cfa0a99f73f3e3a74bb4b3c0d8ca2abd0e1c2c17dafe18b4ee2298
                e87bcfb1305b3c069e6d385569a4067ed547486dd1a50d6f4a58aab96e2fa883
                a9a39e1bd45541eee94efc32faa9a94be66dc8538b2dab05aee5efa6b3b2efb3
                fd020fe789477a93afff9a3e636dbba864a5bffa3e28d13d49bb597d94865bde
                88c4627f206ab2b465084d6b780666e952f8710efd748bd0f1ae8f1035087f50
                28f14affcc5fffe332121ae4f87ac5f1eac9062608c7d87708f1723f38b23237
                a4edf4b49a5cd3d7
                00000014
                dd4bdc8f928fb526f6fb7cdb944a7ebaa7fb05d995b5721a27096a5007d82f79
                d063acd434a04e97f61552f7f81a9317b4ec7c87a5ed10c881928fc6ebce6dfc
                e9daae9cc9dba6907ca9a9dd5f9f573704d5e6cf22a43b04e64c1ffc7e1c442e
                cb495ba265f465c56291a902e62a461f6dfda232457fad14""")
        ),

        // Test Case #5
        // Additional Parameter sets for LMS Hash-Based Signatures (fluhrer)
        // This test should fail because SHAKE is not supported.
        new TestCase(
            new InvalidKeySpecException(),
            false, // expected result
            decode("""
                00000001
                0000000f
                0000000c
                808182838485868788898a8b8c8d8e8f9bb7faee411cae806c16a466c3191a8b
                65d0ac31932bbf0c2d07c7a4a36379fe"""),
            decode("""
                54657374206d657361676520666f72205348414b453235362d3235360a"""),
            decode("""
                00000000
                00000007
                0000000c
                b82709f0f00e83759190996233d1ee4f4ec50534473c02ffa145e8ca2874e32b
                16b228118c62b96c9c77678b33183730debaade8fe607f05c6697bc971519a34
                1d69c00129680b67e75b3bd7d8aa5c8b71f02669d177a2a0eea896dcd1660f16
                864b302ff321f9c4b8354408d06760504f768ebd4e545a9b0ac058c575078e6c
                1403160fb45450d61a9c8c81f6bd69bdfa26a16e12a265baf79e9e233eb71af6
                34ecc66dc88e10c6e0142942d4843f70a0242727bc5a2aabf7b0ec12a99090d8
                caeef21303f8ac58b9f200371dc9e41ab956e1a3efed9d4bbb38975b46c28d5f
                5b3ed19d847bd0a737177263cbc1a2262d40e80815ee149b6cce2714384c9b7f
                ceb3bbcbd25228dda8306536376f8793ecadd6020265dab9075f64c773ef97d0
                7352919995b74404cc69a6f3b469445c9286a6b2c9f6dc839be76618f053de76
                3da3571ef70f805c9cc54b8e501a98b98c70785eeb61737eced78b0e380ded4f
                769a9d422786def59700eef3278017babbe5f9063b468ae0dd61d94f9f99d5cc
                36fbec4178d2bda3ad31e1644a2bcce208d72d50a7637851aa908b94dc437612
                0d5beab0fb805e1945c41834dd6085e6db1a3aa78fcb59f62bde68236a10618c
                ff123abe64dae8dabb2e84ca705309c2ab986d4f8326ba0642272cb3904eb96f
                6f5e3bb8813997881b6a33cac0714e4b5e7a882ad87e141931f97d612b84e903
                e773139ae377f5ba19ac86198d485fca97742568f6ff758120a89bf19059b8a6
                bfe2d86b12778164436ab2659ba866767fcc435584125fb7924201ee67b535da
                f72c5cb31f5a0b1d926324c26e67d4c3836e301aa09bae8fb3f91f1622b1818c
                cf440f52ca9b5b9b99aba8a6754aae2b967c4954fa85298ad9b1e74f27a46127
                c36131c8991f0cc2ba57a15d35c91cf8bc48e8e20d625af4e85d8f9402ec44af
                bd4792b924b839332a64788a7701a30094b9ec4b9f4b648f168bf457fbb3c959
                4fa87920b645e42aa2fecc9e21e000ca7d3ff914e15c40a8bc533129a7fd3952
                9376430f355aaf96a0a13d13f2419141b3cc25843e8c90d0e551a355dd90ad77
                0ea7255214ce11238605de2f000d200104d0c3a3e35ae64ea10a3eff37ac7e95
                49217cdf52f307172e2f6c7a2a4543e14314036525b1ad53eeaddf0e24b1f369
                14ed22483f2889f61e62b6fb78f5645bdbb02c9e5bf97db7a0004e87c2a55399
                b61958786c97bd52fa199c27f6bb4d68c4907933562755bfec5d4fb52f06c289
                d6e852cf6bc773ffd4c07ee2d6cc55f57edcfbc8e8692a49ad47a121fe3c1b16
                cab1cc285faf6793ffad7a8c341a49c5d2dce7069e464cb90a00b2903648b23c
                81a68e21d748a7e7b1df8a593f3894b2477e8316947ca725d141135202a9442e
                1db33bbd390d2c04401c39b253b78ce297b0e14755e46ec08a146d279c67af70
                de256890804d83d6ec5ca3286f1fca9c72abf6ef868e7f6eb0fddda1b040ecec
                9bbc69e2fd8618e9db3bdb0af13dda06c6617e95afa522d6a2552de15324d991
                19f55e9af11ae3d5614b564c642dbfec6c644198ce80d2433ac8ee738f9d825e
                0000000f
                71d585a35c3a908379f4072d070311db5d65b242b714bc5a756ba5e228abfa0d
                1329978a05d5e815cf4d74c1e547ec4aa3ca956ae927df8b29fb9fab3917a7a4
                ae61ba57e5342e9db12caf6f6dbc5253de5268d4b0c4ce4ebe6852f012b162fc
                1c12b9ffc3bcb1d3ac8589777655e22cd9b99ff1e4346fd0efeaa1da044692e7
                ad6bfc337db69849e54411df8920c228a2b7762c11e4b1c49efb74486d3931ea
                """)
        ),

        // Test Case #6
        // LMSigParameters.lms_sha256_m32_h15, LMOtsParameters.sha256_n32_w8
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000001
                00000007
                00000004
                0dc6e2060bd57f6893d7934b26515ce751360f93dd74a648fa015aa79c862407
                5ae5daea402617abb48a1f6b9e2c9f28"""),
            decode("""
                466f75722073636f726520616e6420736576656e2079656172732061676f206f
                757220666174686572732062726f7567687420666f727468206f6e2074686973
                20636f6e74696e656e742061206e6577206e6174696f6e2c20636f6e63656976
                656420696e206c6962657274792c20616e642064656469636174656420746f20
                7468652070726f706f736974696f6e207468617420616c6c206d656e20617265
                206372656174656420657175616c2e204e6f772077652061726520656e676167
                656420696e206120677265617420636976696c207761722c2074657374696e67
                20776865746865722074686174206e6174696f6e2c206f7220616e79206e6174
                696f6e20736f20636f6e63656976656420616e6420736f206465646963617465
                642c2063616e206c6f6e6720656e647572652e20576520617265206d6574206f
                6e206120677265617420626174746c656669656c64206f662074686174207761
                722e205765206861766520636f6d6520746f206465646963617465206120706f
                7274696f6e206f662074686174206669656c6420617320612066696e616c2072
                657374696e6720706c61636520666f722074686f73652077686f206865726520
                67617665207468656972206c6976657320746861742074686174206e6174696f
                6e206d69676874206c6976652e20497420697320616c746f6765746865722066
                697474696e6720616e642070726f70657220746861742077652073686f756c64
                20646f20746869732e2042757420696e2061206c61726765722073656e736520
                77652063616e6e6f742064656469636174652c2077652063616e6e6f7420636f
                6e736563726174652c2077652063616e6e6f742068616c6c6f77207468697320
                67726f756e642e20546865206272617665206d656e2c206c6976696e6720616e
                6420646561642c2077686f207374727567676c65642068657265206861766520
                636f6e73656372617465642069742c206661722061626f7665206f757220706f
                6f7220706f77657220746f20616464206f7220646574726163742e2054686520
                776f726c642077696c6c206c6974746c65206e6f74652c206e6f72206c6f6e67
                2072656d656d6265722c20776861742077652073617920686572652c20627574
                2069742063616e206e6576657220666f72676574207768617420746865792064
                696420686572652e20497420697320666f7220757320746865206c6976696e67
                2c207261746865722c20746f2062652064656469636174656420686572652074
                6f2074686520756e66696e697368656420776f726b2077686963682074686579
                2077686f20666f75676874206865726520686176652074687573206661722073
                6f206e6f626c7920616476616e6365642e204974206973207261746865720a0a
                """),
            decode("""
                00000000
                00000000
                00000004
                96755d5f8af0aa32419be743afe779842db52a82387fedd67881aec7172db8c0
                70734189eadc76de06e2fc999e8ce42e7ba68c942515b4547abc8c6659a42fd1
                371b03ef9ddafdf755b4ea374bfd00b259baef59fd87fd23b1ca5c254ae54fdc
                65eec03046b0ca68e8168f82c2e7d28f456ab4f3c69c67ff550cbabdf0f25437
                c890347db9d87e0fd243a0341bd6d6cde5190d3c3e7a249bfb757228fe6353f7
                69ce313fdaaee88c0416622625f3b6206c2302633e81c23f81de067393a3e3c8
                537d3b6b800e7ccea2d90787fb8b7c73cbdf1e778044786ad3b47cab75f9238d
                4ef8913fd5ad1e9f762200a649c3e42915f66210c6674a9c0f5a1dc780607b36
                c20ea9e299b2dffa4cd144d0715d18cc7130736ebaa67db1c69336ac3c4295a9
                94c725bb75a5638a569399f4905f39ccf87999e053fb08ac6e3c04dbbb9c9196
                121306e02152603817a574f15dcf010ae68401367024a62ecc4c0dc68bbc76df
                c604978101c1ebe4f5fbd8b0bd14aded6740c60b3cb18f8d166c4857940ae8b9
                25b707b56637499984f24194cdb2aa66b1bb80a679a8a40bba732ef19b80c301
                91bde4b32c6ec0267d81dc86a07ab30afb24b422e99285faae17e8679cf3a6c3
                c1682a1d91afb678700fa40fabf03e8b795bd06c4b0b2f8f124ee6790f5aa410
                5b8eb7b845efddad84488ebaae01ba7f28e9c670bdf4a142305f3376d13c1a55
                cb3e8618e05c11bcf244ae51b825d221e372f45b9c0f512a8ebbeea9a01f213b
                75f69f8090f50c11f13ab092c947ded0689803c834c2bbc8b80310be3ad15972
                b745678270a0a670806ff1dfb00c65bf661356634bd9466946d79e7e59d4bb63
                008f22127172f54c7c15531003a7d31dd949ba15a401b6e8b574f462ee296fba
                00e523fe2c54b4b763eb18028d49f5fe60fa88c7c26188710a2ef040c2641c1f
                0499a41352ce2c397bee4fa7ffc84a4834419bc571bcfa9ecf17e5e30e14042d
                424d0651e85b826e8bf592285e83d01b8b67eff87caaca112fd67a1ed4d5ae7e
                2a587d4390c8fd1ef366ff80b83ab02973bcce0be3352656c7e07dfed0df04df
                f886adda121035831bf24c31a47157fa19f1e29a7c329a8c0647365886d7914c
                969f5477211713dcfe40883c9e00037b200190aa8bd6441e2e9caf895b9ad8d9
                52dc7e2ccaaa1d5181b1554c90da50f53bbc993e9a04caed8aff848b72470d1c
                7deb858d9baf393f63d8f85cf570161d74a12c93de618d0b1112d5c73164358e
                92ce5e4d344b0c9a20e045b47f7ba00567b49cade32612222a5178e5868d88a3
                3ec703145ba626a497a716db4ef391cd6c4061dbb904bcbf6ce0e9010893d15b
                991b3b0b1d48e2973a19bd94ce2de05577e3db8961dd40c2107ad39aca37e101
                66a1788260c7cd125e615b0f19493923a17c0bdba2c982a6ab29fc28689e2b55
                c6460afe1e49332835228ea102e9bebf60fe64e44dc1643eaf49c569331e8ca3
                d4c7a8206d4b088d786aa514322d9030266fd52d5b92170a112c36b86117f8e7
                37520d176ee71b88a13e22b74afb78915c8516bc2967b46b350c6cb5462cd3fe
                00000007
                7547b8dd925185bf123233ebd5d6efb5b84c25f193ccc96f3cf5746053054d09
                274b10a2e41c5b137cc6e3008f6fbe13a32b41ba0ca3d5b95d2ca2af3a7791b4
                e4e80d0a837cc7ca2660105679d28c7230bacf244b74e89c9d1a00ec30a96d12
                5ff86a045d8d1ce2cad8df211cea8053336b35a4ce75ea9b6a3693c906486a2e
                f978e4bc95d39450ae4c44b5204a0f463061cecf2f4b5a3182b40305b57d9bf9
                130d38397d257fae6eed3ccf5ed8739738f948aa1e99ada7da70e2f4cb090758
                21323fd7b9934344bb47a53196150b88c4e016363132e798a5949a2b52e7194b
                d4babc9dc13749f7e69ed462f42de21e03186b6e13aa496b73784d071f8f8292
                63b37b71c24b1316bfe5dc48d19cfb4a3dfab112311cbedb59de0fc6d139676c
                b5cca0ed495ce2251895447f4983d8147999a9e8a3fbae038e0d3c941b81bdc9
                6a80dc3b8569237837940d148150c400d2a93ffe7f2b62aee591498b6c659cb3
                1da85478899ad1bfce0803419a4b5bfcfb0ffc27481c351dc594af1146d1ba70
                127968e379d34fa22c03a1ca9f2cd8d2f255e9ee2058a6b018cc464d758d633f
                f4197291b1ad4257f8f76e1633c19f77fc361767a7a3804d5607931d975d3b19
                5182fd0867719ce10daf0f0c0d52b16b8088ca9a26a22aa05224a1765fc82961
                """)
        ),

        // Test Case #7
        // LMSigParameters.lms_sha256_m32_h20, LMOtsParameters.sha256_n32_w8
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000001
                00000008
                00000004
                c8568f619f0d5429eab1e63c80e058d1b8a326640a6ab457d776c52eec545dd9
                7fedc7e225ab0cce270d961ff9b1615b"""),
            decode("""
                466f75722073636f726520616e6420736576656e2079656172732061676f206f
                757220666174686572732062726f7567687420666f727468206f6e2074686973
                20636f6e74696e656e742061206e6577206e6174696f6e2c20636f6e63656976
                656420696e206c6962657274792c20616e642064656469636174656420746f20
                7468652070726f706f736974696f6e207468617420616c6c206d656e20617265
                206372656174656420657175616c2e204e6f772077652061726520656e676167
                656420696e206120677265617420636976696c207761722c2074657374696e67
                20776865746865722074686174206e6174696f6e2c206f7220616e79206e6174
                696f6e20736f20636f6e63656976656420616e6420736f206465646963617465
                642c2063616e206c6f6e6720656e647572652e20576520617265206d6574206f
                6e206120677265617420626174746c656669656c64206f662074686174207761
                722e205765206861766520636f6d6520746f206465646963617465206120706f
                7274696f6e206f662074686174206669656c6420617320612066696e616c2072
                657374696e6720706c61636520666f722074686f73652077686f206865726520
                67617665207468656972206c6976657320746861742074686174206e6174696f
                6e206d69676874206c6976652e20497420697320616c746f6765746865722066
                697474696e6720616e642070726f70657220746861742077652073686f756c64
                20646f20746869732e2042757420696e2061206c61726765722073656e736520
                77652063616e6e6f742064656469636174652c2077652063616e6e6f7420636f
                6e736563726174652c2077652063616e6e6f742068616c6c6f77207468697320
                67726f756e642e20546865206272617665206d656e2c206c6976696e6720616e
                6420646561642c2077686f207374727567676c65642068657265206861766520
                636f6e73656372617465642069742c206661722061626f7665206f757220706f
                6f7220706f77657220746f20616464206f7220646574726163742e2054686520
                776f726c642077696c6c206c6974746c65206e6f74652c206e6f72206c6f6e67
                2072656d656d6265722c20776861742077652073617920686572652c20627574
                2069742063616e206e6576657220666f72676574207768617420746865792064
                696420686572652e20497420697320666f7220757320746865206c6976696e67
                2c207261746865722c20746f2062652064656469636174656420686572652074
                6f2074686520756e66696e697368656420776f726b2077686963682074686579
                2077686f20666f75676874206865726520686176652074687573206661722073
                6f206e6f626c7920616476616e6365642e204974206973207261746865720a0a
                """),
            decode("""
                00000000
                00000000
                00000004
                c89e20317ae8d2211c381fe7354bf382a750e37c307588a30d1bb9a5868e0fbf
                414378b30f8c59ccb95a603f03679a417c01bcb191677d629c37a396ffe313c7
                f27f1553e993102d1b311b92fd7669c2d1ada6cc808c11477c86fa928196028d
                3855d6a39fb56a73ac8eb812fa1974778ab1a7838eda03e4b7ff32d8faad4574
                1ede66888334c584ae4086d6a5446772c3f18041126e1972d5acb593261a0a5f
                2685e71bb75fae408c4d8dc359bd723d97b5180d96d57a9edbaa7a74f2ef4aa7
                316bf4b8232bb32b32bfd3e6c4b7e1356d5822fb90b8c861e8ef9a1f7cd67b32
                2b632c2565a6ac6e3635568f1c2cda59cf4ea6a83ec622f81dda9db0b91fff87
                080fc8b29ee5514cb25c943a714bd298ea0bba527decdd546c76151b0a9b6c60
                ef9de9b8aabc3c979f08fa1a613682eec4c564e5c0d87e932bc618b6009ad575
                59489d25a58a4338a03c9ed4c8a89eeea418d5b4a7f813eaf163e2530a40ee84
                9b8893f91a2f5aeab8ddd32ad8bbb8e0ffac69d0f1b5333d5211d11f32cb89a9
                9f7f346aa5f3c68447e831dfbce57a0a90ac32cb59066f1e5a0c7eb6bb5ef4d2
                e941f0a8ffe6e8e944cecdb7124a866e4282ecd848bf53f94f0323828a2250f8
                9a59dbc5a0dd02fc90fc433219ce64e982d86c5ddf3bc8bff3ac7f2c6e5dbab1
                50a2f4371ebab285f70e25fb0f64667c5805381ff1031321e6f8cb1c85250393
                5db51e0032f2da99c0bcc22cafe3abd1d7fddf676713a7fcf2388ed13d60a8a1
                ccfb996d9d0accf2be789b949f8cb8ee895f870c4b0c4280a99713abfa29377b
                b22a6cfa5f3e0bfd7b6b65c395ac40f8e88980fce0c4ea54b55271a6b3a78a15
                957cee33a498d237cb0b6457e7b539591b4c3c01b3ed1e75308cb2d85b1a5d46
                40086d1f3c01a244516beb5409175179cc112c16bef7041c92be7e25843703b3
                c9fdbb1e9e2d880f9e2c54b8cec53bff94d15406cdac5793dad36af28abf8f16
                d95606410d7a8b2af01ddea64572d77609bc9b1f26e0f61ed6115709edaefe5e
                c3c44e62d93ffb3dd868f799de31fdd82a5ddbf9126a5271fb368624ecfcba8e
                859e9d644063e2d016f3bd6984495bb67a92295a7ba6958d09fb3fa5415cdfbb
                c25605e991a5a6d914199f50842357226438c5d757cf1d0bbffdabd32cdb1559
                3167d8e672bf1653c2c7466ab7a84e4b587e62be7fe73270cda64513e39966ae
                71108c4d187175ae14d9d36dfe6754770febe70c88515b108a20e4fddc240531
                3a47377388104fb2597ab1c6ba6aeaf96bf9fa3ef7aa8fc61fdb46fb4c99d401
                3ec90362b3b2df6b9be155ffd520f696950ac931d1d32c1fae4d0da0ef76065c
                5070edf2043fc9b03ec3c250594f92b8f80862182501a4cb2637d79d347cc2cd
                966a3d20415bc34a870d0e9479da62a820f7973e8737a3d1f1cb04507d70981c
                5f30c0319443ba0a03cab8c8460306b5a66dd1efe29956995ab4488ee20ceb83
                c0891c84eb01761ed74d514d483a51e11a938e89e1f1e6b4a23ebc71150b9381
                c01faf71b0ab4f2842f342b80d6bb58bb410091e314127d1c33520e5f38f02f1
                00000008
                44feef16ea19cce8296b02d3955c58a873f2adb5672f5408d2a1c404c0a955a0
                45f483df1000b0cd4dbf5c7918b5259866135714df5fe538b26c10d19b29c5bd
                3e6d7f04bf4544385f9fac1e216e371888f866ac840a83518f940f1d0c77487c
                eca40d5fec7174006e7255ccd1a85fbd745c0d17797e0425edf97a92b8eb3b79
                e524988e3aaaeff9ae4762289737a39acf87e381b5cbe4fc2a0ec29bb3fb5dfd
                77c4ae959b46e038e7150c7bb26613125728dbfaf900aa6696c6f2ce0f590c30
                14d6b61f70897b732564baa09674b12ff2412eae3378a15ca8ab7d79af7372b3
                3dad3878699eda11cc5265d591a2b00a5d948c38fb4e9c4bfa2f34328d590c19
                26396c9db7355037bb9e7fa0918e9d2467d358898bad77ed3fdd2cb6f0d14f92
                67ba57212ea080200635419bec21ac4163aa41209865b212c5f4b000968e2837
                f55c5dc8389f44824427b4e6cda1917e73f7fab883717a0304373e95a7118909
                aa1e7854e2546c766823e2f2f4a52f001763692bdb45675ee65101f10007fdef
                b5205b6f2d74c42396a7de0a55ff47855e50cea65f46549f845ff855ca6bceee
                450eb11b7932b6465736893ca654e1faf280ec2da99dad0b833f5c4e7d805af1
                0e95359ba4e23b2640e2768075815adb2298fb5d1dd3552b0e868c2b69a92da3
                8b83713af275e933354c5a02438480004d26d0667c3d31236f2e42e594b3108a
                2631d63f6b0d6abfa0cc338294019b38bed8da4b49b0ff1a64871ff648687c5c
                97b863b78eb60844af1e94d6d3ffbaeead48a974e65fff24776553b3dca6c7b3
                072a39cfd09a8bf9c7591c605659c1b103288486475f54be0fb80c18717a944f
                51b6d317fba486e1e0ab5afea205335836e717a185827ea4cd47d557be53cc4e
                """)
        ),

        // Test Case #8
        // LMSigParameters.lms_sha256_m32_h15, LMOtsParameters.sha256_n32_w4
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000001
                00000007
                00000003
                7bce4db5bd53cb23819d0fa2181e4d441453ff821284c9d83b8ddace22581469
                593d6dd0aa2c99feddc84f8242f6a002"""),
            decode("""
                466f75722073636f726520616e6420736576656e2079656172732061676f206f
                757220666174686572732062726f7567687420666f727468206f6e2074686973
                20636f6e74696e656e742061206e6577206e6174696f6e2c20636f6e63656976
                656420696e206c6962657274792c20616e642064656469636174656420746f20
                7468652070726f706f736974696f6e207468617420616c6c206d656e20617265
                206372656174656420657175616c2e204e6f772077652061726520656e676167
                656420696e206120677265617420636976696c207761722c2074657374696e67
                20776865746865722074686174206e6174696f6e2c206f7220616e79206e6174
                696f6e20736f20636f6e63656976656420616e6420736f206465646963617465
                642c2063616e206c6f6e6720656e647572652e20576520617265206d6574206f
                6e206120677265617420626174746c656669656c64206f662074686174207761
                722e205765206861766520636f6d6520746f206465646963617465206120706f
                7274696f6e206f662074686174206669656c6420617320612066696e616c2072
                657374696e6720706c61636520666f722074686f73652077686f206865726520
                67617665207468656972206c6976657320746861742074686174206e6174696f
                6e206d69676874206c6976652e20497420697320616c746f6765746865722066
                697474696e6720616e642070726f70657220746861742077652073686f756c64
                20646f20746869732e2042757420696e2061206c61726765722073656e736520
                77652063616e6e6f742064656469636174652c2077652063616e6e6f7420636f
                6e736563726174652c2077652063616e6e6f742068616c6c6f77207468697320
                67726f756e642e20546865206272617665206d656e2c206c6976696e6720616e
                6420646561642c2077686f207374727567676c65642068657265206861766520
                636f6e73656372617465642069742c206661722061626f7665206f757220706f
                6f7220706f77657220746f20616464206f7220646574726163742e2054686520
                776f726c642077696c6c206c6974746c65206e6f74652c206e6f72206c6f6e67
                2072656d656d6265722c20776861742077652073617920686572652c20627574
                2069742063616e206e6576657220666f72676574207768617420746865792064
                696420686572652e20497420697320666f7220757320746865206c6976696e67
                2c207261746865722c20746f2062652064656469636174656420686572652074
                6f2074686520756e66696e697368656420776f726b2077686963682074686579
                2077686f20666f75676874206865726520686176652074687573206661722073
                6f206e6f626c7920616476616e6365642e204974206973207261746865720a0a
                """),
            decode("""
                00000000
                00000000
                00000003
                92f6ada5a00376437675ae462a5c40d4b1123d97352fdeb3f0b6dd741a3e4db7
                60ec956306dc6f2900f5e70e427265deab2d979ebef270cb61fac22a6b6ddc78
                ec265af6ef86c9513f3de135f674948c08e5cda0cb953fc6846e2720eb669fd6
                2d7e1fd5de25ea3491a4d018782cd929f8df2684ed20c2f71950ac606e86a475
                4f60cfca6810ff34233425f932bef13cbb334981f71d54ea71de3510ee52fd31
                85cb24191f426bdec8c10caa831e74498ee52dece68f886e9b157b68f2f521b2
                17e5907d824bca542126b9d79c70ed4cb1431146025e42ecf8f12b970f109f79
                f74e4af4511d966b032976f93ae8118cb4cd3924e92bfe5f508101b0abdc53d2
                6f5c1720448e2efa4b97aea98e2069eb4495e2c093601fe70193ed469162e976
                4ca3cf64866c59189a488a9e7a510149e0e9b92b0fadcabf22140841946997c4
                51410e4954faed09bc3e3e5dfdcfd598e5e70966356718230db6ea8bdf22256d
                fb337383d342212b6fbe4be731aef548e86e628fb372971e20d878d3f003b06c
                b363799967396381c34252b306a67cfd469710aa9664222dc22fe41008919439
                9394d75ffaeeac3b621b0643de2e98f723a761ff6e695f673c3d70918247ef5c
                39f222ddedaccc63b6da7ebdfef33a1aba8df3e2444610f6c7b72ba15352f783
                b1cac31841e58f6f22a78e2f8521379e226c6890fe41682f2c2a74a44f619f05
                84a343deff837d809e41e82cc6ca0b568eb4dfaf5df71de6ad488a3733d07db9
                a1d4137cb10244167dada62b3300e5329f886bcf2d35568f39ec759ca82687d9
                37fc2b6c7b22d0016f856d9ab353503399bd525298a460b5e5b748942affbe5d
                5f562145c6018f53175bfb1c145e512baad481193889f487eb6a007ab3fe7d0f
                3e7298c3519fb6d6f6f1c493f5e18bf64e5d421ad60b5c01b599aac05355e674
                1966c4d95f3fe002fb936c948121db57a921baef1d9aca2aa918641705a7618f
                59df64211e2a0043088af6e970e6e87997a7f9b1fc85513dba92dc8d1f990ada
                a8694c4b408341d2e1e01e07ff329486d322ee9b5fa7c3498558c503358f29ed
                7abf96c1928b3d21f5262517800587d01517a3ec8fbced7d3f0f7523e6544d7e
                2cc5afb15b709fac35301940ca8cf4ef09abe8d1b175cdaa84caba9dad175890
                ed9f51d762881e16cec7fed78fe9324bea6b372c09e1ec8078c73192e6869830
                5f16d35fdbe5ce305473399014e15c1011329723df62e3f39d28b9e9576e16a2
                1199b8eb076017b0500ec5da3e33789a48f6115856a586923f23f167912a307e
                19cc96697a4c3198211e95defbc541b797718b0a59bd330a069a3eadd2c285e6
                275dfce172e1a109614f0a23ca45fe29dbdf943710176ac138ca10e8a09d87f0
                b59ecc4073fa253e0b6707557d075175a167bd0390e089b300d3106c21e83aa0
                0ab384f9b301da07eba951294252a421e373def9205711db8326e1558bbd7a79
                3665e5663ecc7a552223edabcc6333f07095f274d3e3aa8ec114b8e124aaac0a
                4e99113d1950ddf46edf9ea8c8af0d9fd72146885e381a9d899b84d404906db5
                6f7f3c209be9da130b0011de9916f7d63c3172637ad83e9a9566d7938559fae3
                ac94602daef9f18a8d908e55dc962c48aeaa49c29b4fdb2a3ab38802f650225b
                8e6b3d25d3590ed13c3d22e3f1253d385fb02b1feee7770b0f5eb66df2558d5f
                3fa6a247e297e96b8f8263648f5f469c8e5313fb929c801e61a16ebe10286010
                2c2e9e1a759b7b1f4b2586f3e916970479a0d836c5465c1345314d3dacbe5ee1
                074d3a15e4bcf37051d9b1ad76ffbf5bc2826c2311e53caa05d22a7d0315f096
                51be069f7903a00b3a1b38a632b00b2e26d3946d151c6a7413cdd7a12593ee70
                6307d8dc0d822da33b7e335bc674d287f944fcfbb12af664ef889c9bf9e1175e
                f4aa47736532b9a79c489e0130fb2f49456f189cdda60e480ff0cc8435537374
                e7619db6a06a6e2eabbbf861483003673f5998dd31c722a6c7d17c7e43d94acf
                311e726e12c51805db6837392abcd880b2c5ad035b04bfca7b16ba9b82212c7d
                6920b91da7c1e6eaca690756d9b0d403c67ab0f258067f1ae537ad794bb2f2ac
                f1132d35e83bc8de5d542f100ea092c33499c5577617dd56399448c6e0719751
                f322e16abf3e792ed0014b49354e697f3d4d78b19431d92cbc97aa12bb9f339f
                d4be05c666b3215e8450f4f27e5a1a705eaea74c312bfa7d76d4ab39d957fb33
                1badfe1cda019460c5011897430e14ad9e8101a5b25fd3de752f47e97452f8d3
                2170946b4c905f613953b6a7ea03b96d67b9bee3651bdf1a33f63d2d8bce2867
                2e90ef39a066557e991c78201353aeaa47094542581506a5ab258f8cc7757738
                9f3d8fc5c19f91b5e7ec60e88bd8c6665b649ac696341229cc1b2cc2224c5da2
                7f2c57a53f5824aad8198d9cdefd13753ce10f8cc14f1b764b19d247e6872fd1
                99624aaf8a3def3cbb72ae940868870cfb29edc1354532df028dd61414a107ea
                83e5a86aad58f52306ada8c12e6817a870696cbec7f5d4ac6a8c5cf63a0494b5
                b51617e04794cdd195a1dbaf105175d0219b97fb15efcd172cb3084dff951a69
                0b2a0490f8641f245b03a67e4cd75783a8a668483789a3386899d888806633d7
                023ce6b77ee7b4c011be72d52eba18cc5f87fc702c9bbcad61e829c78b8faa6c
                c6882cfa49bc6ce98f8975bcd88c5c3213c422f20294b4bf960c79911744e18a
                1e54b91c78ab7de3f2f5bc69b20a68d7c76f9b0ac029355c523db8348e9bd854
                5bc86e1005eb48819993dc2eb96712a71528cfcd69b38c44f668b2ae1e74d985
                2303619ef5f54d927d41db399f7273a8d42c85fac74705880b50aee227dab2f4
                a2c96d9d6a0cd66ba0c062796c085b0d351b203421f0c1b4ef61330faa31c5ce
                f1e91688a35b7c5feb455d467c275049ec330af627d90ac89c7696d9ba06a402
                69c347eb6e9114d0f95f0b7a0e3058282988cb2c2cf33ac135d3b108670b8ca0
                90e8a770cc1b522fcfd3a777e6efb2e658743a9e65f8dcf218828556626d87f4
                00000007
                8f421eba67cfd61a355895a87fae815f4531f5ad25dd1832a672321836cbf772
                77099b27748d0dcbd08bbd4dd5bc67ff64d630c41bebbd7bf1072910745fcb42
                867b9b5e07a6614df38dda0a0f4c89b393b27f42e0a850852a75e9f178e0ee70
                f490bdb5266af9db46a2ec1e0fcc6ccb609808cf460048df6a5e8c8fa864ecd8
                c87266261ad5e79c874731e2c00a9efc9649895f30ff4a46cc860ebdbeba90fd
                c47cdd5e8bb67584f2758b25b032e1462caca6e1f026ecd855856765ebe87f04
                4e749f257bb5defbfb826cbfc5de07baffa680e520ff8663b523f151bfb772c2
                a6297ab5a9977fb15367eb960050d4c23e42bfc89ac812c1e6cd9ec37668e37c
                1a594e0ce90de5cab6341b1e77ea447521645632912a0682fc60de7f7831d0ae
                72604756a4416573c0cf6a52e0868b857b76b1faa5bb7e3607a339dd32f33263
                4b210e0e31da922ac6870ace1a068f3418588071996ff816b95a1a478488f82f
                b54e27fd3f7037360b20e1b3e8674325253ffff578dbf8108927a966cf4163b0
                40aa99d98df801f0e241c1607d9e8484c9755f6bbe299a6efe96ec0836e9d53c
                213db6d352863854781c78c4cac3083210f979d3f7884aca69fa83429c1542a5
                51b8e95ffad4f89b506bd31ba613fe66a375434114dfbdf11741a8d86a239ded
                """)
        ),

        // Test Case #9
        // LMSigParameters.lms_sha256_m32_h20, LMOtsParameters.sha256_n32_w4
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000001
                00000008
                00000003
                fe732f6abc16f3b1c0d1b78d9e72fbe118904abe9b33f2e03d0728ff4cf15b3c
                ebea5149fe955d36f911e528d2aaff42"""),
            decode("""
                466f75722073636f726520616e6420736576656e2079656172732061676f206f
                757220666174686572732062726f7567687420666f727468206f6e2074686973
                20636f6e74696e656e742061206e6577206e6174696f6e2c20636f6e63656976
                656420696e206c6962657274792c20616e642064656469636174656420746f20
                7468652070726f706f736974696f6e207468617420616c6c206d656e20617265
                206372656174656420657175616c2e204e6f772077652061726520656e676167
                656420696e206120677265617420636976696c207761722c2074657374696e67
                20776865746865722074686174206e6174696f6e2c206f7220616e79206e6174
                696f6e20736f20636f6e63656976656420616e6420736f206465646963617465
                642c2063616e206c6f6e6720656e647572652e20576520617265206d6574206f
                6e206120677265617420626174746c656669656c64206f662074686174207761
                722e205765206861766520636f6d6520746f206465646963617465206120706f
                7274696f6e206f662074686174206669656c6420617320612066696e616c2072
                657374696e6720706c61636520666f722074686f73652077686f206865726520
                67617665207468656972206c6976657320746861742074686174206e6174696f
                6e206d69676874206c6976652e20497420697320616c746f6765746865722066
                697474696e6720616e642070726f70657220746861742077652073686f756c64
                20646f20746869732e2042757420696e2061206c61726765722073656e736520
                77652063616e6e6f742064656469636174652c2077652063616e6e6f7420636f
                6e736563726174652c2077652063616e6e6f742068616c6c6f77207468697320
                67726f756e642e20546865206272617665206d656e2c206c6976696e6720616e
                6420646561642c2077686f207374727567676c65642068657265206861766520
                636f6e73656372617465642069742c206661722061626f7665206f757220706f
                6f7220706f77657220746f20616464206f7220646574726163742e2054686520
                776f726c642077696c6c206c6974746c65206e6f74652c206e6f72206c6f6e67
                2072656d656d6265722c20776861742077652073617920686572652c20627574
                2069742063616e206e6576657220666f72676574207768617420746865792064
                696420686572652e20497420697320666f7220757320746865206c6976696e67
                2c207261746865722c20746f2062652064656469636174656420686572652074
                6f2074686520756e66696e697368656420776f726b2077686963682074686579
                2077686f20666f75676874206865726520686176652074687573206661722073
                6f206e6f626c7920616476616e6365642e204974206973207261746865720a0a
                """),
            decode("""
                00000000
                00000000
                00000003
                4da59d6dc70132d671114d7e3fafb184a898a603a60119dcb55148775618754f
                e4d35a9594db68ab5003f967e968b97101307219d000d16dbc58bcc2285ddb23
                dd983189c34e7defa68a7682e26c50668bb20c6d1340fbb127d1d5b805454a66
                d2a7f43d2a579568bea4187ae180cd1038d80655454c5dcb9700821d55db70f3
                aea2d5f8e1b1b99cfaa86b253e13a72af3389d33bd51329171d666c260b71a66
                7f1f7e4256906c0247ac361fd2cb11ab9f3d8d042bab28fd5df69f14a54f88f3
                4339c81c02a0a9256292566ed753d2f4312d4103bf25f7b310a86f96301143f2
                47c2291674797bd1088920eb5bdc049b8f9809c3c6d96ebfe49084d1021aadef
                ec3ece50b4b42e4af022038c6b5363c2dc24522a2e641e352363d9149d68cac3
                5bc3d973071e6b7aed2f4f96ca91aa22e162ab985baec0e56df59d3e8b37fe89
                59c00aeab5f3713093105cf7db782fa45ea8ee2ec8feb56c529e6b42f18043f9
                79d8c4c9d56a4255ec9747d31cc62a7bf70cd8777ffb75ee43dbb2c394e36525
                71ce27770f6ea480e89f05ba3cdc633cc3ed269eb2ca0131268fc0579d738334
                504b477e8fc2264c421e2e551b1a93713281858ba2cb588a2adcce4d11c2700e
                31d7ce6936b267ebe96b4f9bbc4763cccb4f245d371177c258c6d23c30bc5dca
                0f6d8fd8194b683f2ac4e0f3ddb7ba1cee1c47e5eec239f5a6b661aaafd2960a
                5d1b3305e53d9bbf8f6413368b7656723bcd47e7ab18b41d7e29b511767ba267
                e662ee59a004264ec2f88b8ab6fc778315ebc2a08144503455b8b2c1bb410eec
                b767ee1eb779f9f2bca35184fa4ff7d8d36a41a9ed3a76fec7fd9a68f8bd35ec
                2dd1aa14e7ed9c41db03de868b266f8cfac7fcc887c78e1aa3e085cf0883eddb
                74d483dd177f9168ab14134a5e2272bca7e6e31185c34225e05de4545a99a4d2
                949a75f970b862aa3b8c9218ac34ad1e3b70a64be25005fd248463606275b9d7
                8d96725b39d8b9ba08acb91c3103c7e31f7970f937e176a34f4f4bc601dd8f5f
                f83caff6c72b2e7bfeefd49c93cbc270cdd4d442be431b8e9b68e011975708a5
                60095a8b784850a3c5bb3bdf2778a8aaca9064b6f1d424f4b41047efc7088639
                73df9b861320e66779b25d134bd4528920b64c79c9eed64d5a01e39f8c2e1a62
                ad2e6e68bc5de644f5f28b09a9479abe5f96bc6d3e0d77763e3cf61feb31f152
                98864c632b450d37a04e6aa4a2fc67406acda826ae98b7c52cc82da5da333ebf
                8b1c771dd994f6c650bf24cea6f25963fcf11a1aab7574b888dc89f92659fcb8
                1aadd497eb6676243eb9f65e66ef97ab8da3fd9678c8166d1b2aeb724211f4c8
                4e72749176f5d63798fbd16b0006dc8d92db7ee411a8ac90eead5a08f89a89bf
                295296b8d1d27e97d472cff51787b67a3d6d2a29b66e672647f395e21c9c83d6
                dbdda3d3608968288224494a815103549960155b1594d36580ca1db90c54b783
                f9e732fc2b14d58726a17bf5869a296985058318adccf6e56672e8dff8beddbc
                dbb4bf30d2c59bcb8eedc0e713cd39ad9bb748e5abe75e05179a856c686d1c6c
                764f8422372d6f2dbe76f277a21c75c7f778a4056a1ff3bb9f0022d931612411
                819c5a8a7b2e360c0ccaaa910e2af0d7ec87e9ba4cb90e85a18ebb8fafb4eec3
                40ee8cfa4b9d9a92fbe07aafd07c58e06baeeb5c587279c2b2c344db5e43d5ae
                32547dc5f45bc855a9dfd6c8d3ff174d0fb999f4819e3b09ca53cb328689486b
                e715f148dcbeea8f46ad00db30bfb087b8ad53ad6e1cb579ed51007289dc5b0f
                691188592e1beb23243ea4ab09771552fd03bafc9619dbba025764a5290a938b
                3c61ceb22c480a0985cc89a790e189b9e0022f772b30daaa218978208ff94c4d
                2f357f74fb93c961de0c97e95efa3a11dfe80caa3fd04051a717bdff5f5cd9ba
                36170cbb14defceac5ce20479b564e0d6a3b978a16cc48ede540212af06de08d
                300290cdeda5a9eb4c35f6b38cdd04a959370a929bb7c3372cd2a3130e1ae19f
                adb54c2389079bd8c7f0249a2b53bf23687cec51b529411fea705fee52504ccb
                fa64f7185c6f3601967d30a644a5fb4051517c8560b64009f6de5993c85516d9
                2c81e47c076a679ce96435939352d67d11fd5bbc94e6e90881d70311cba88a55
                83a7ec14f853f128da2377d14f95b947d00219638e712573223ff1aa2762d1f8
                f93d421e70dfd5ff040eb8f2f435d487e2d91d6d9cc0fe2d9066a01eef8a6c92
                d3a437a8ef5d8c8c5ea5be932d2f71b4d04ab3dacff9508e7dbbba3a01ea63ed
                4b7eb611e35e7d70f0e4ba82ab8a86719bb0e28fc9b3ad28bfa91f227a34b005
                418ae4439814122d5e79336d63e1554e1137642a98e67654ee73031166c34726
                c4e78848a55ae01b1398338217771198db5973527a82ada98aefa954edf09f55
                9f4896aa3b6117592599fd4301dd304d73ce8859509782bf60e8398ff311bcbe
                c26cf13b8a6448f2aebd0ca29d06fca7ea09f559aff3a315b1ee70bff8038b06
                c381c9630896666c83d14b7bc3b07eb153efddc2b09568c0ea3260c26106d1d9
                002ec640f4ed596991b51c20e9d4bb78de99588a33d0a64b20f8ffb2c18b250f
                d5f7ed0a4a5e2a9ff94f62b8e84cb4e2ee898b85c11db384a26e9bddbd300128
                2d886b7485166c7fc982be8c417f8fcd1a7fd98e529f2fe88a46f36d5091af16
                feb75ace51927a339c79cf79fb222b223e845126c73e4e8103fdd04786de373c
                9586d13460949c080ec557345dbb88ff1ca59701c889dfbb6492bb9086f05ccb
                330a2bff05ebba97a5358702a8d2aee46bf1d7fae1b934405d652a05e532b65d
                d7ef2e6599af8683bfe4644ae2fbc77424f31bc422ab791de730e4718d080603
                8dbb78b84c5df001047502acc3efe437cd46b8a0a7b4198d5598dbc343db47ad
                9c379d5147a5d4bb3c8b962a7368a7405a580f7daf312327141cb5681e198795
                3ea2d2d95c7b9ecd6899428a8966203a9ce586357c631f7af27aaef3ea3a47bb
                b8a81a7b83bde6f223b270576427c02f0fbb8e9232b2d70f9a0af16ec6544eaf
                00000008
                ff48f2d9fc8998f7cb6685a1d980003da426db35481323c56195cd5142148269
                5b064c6f98dc3289b8244a5a7cfedf3c1c9a96ae48c79d6c9b59e713e91d90bb
                a0212398645f8c55bbd03832e57397200b8127d6f38570e15297489040fbe0de
                a21d372dfbe5098b1491d8efcfd2389e506039bd49fc976ae940b5e6bf3c394b
                189c63af6e799ed580f999ff02a16a5915f9b2ebee58ecde057345f4557e87b7
                8828c700ec978f41e9b9ea78cf8dc961b0ac33c54d0f91a1d05b4910e981a050
                a2c319c8cf5470b427ab09159fb17e5393176683e6e0b3fcb11104e357d75d66
                6b13356454c5bd0e70c18ccaa775ca568ae50d924c2ba2d74babff2fba05ed25
                4d9e8dff7d8770738f2e666f8db461fbf0ba307cd638950692c65254b03d739f
                72ded6f48b6a9a37de6cb2f3c4498626586ceac37a2644e5fdf9b7794fa4b472
                37bc4d45381e5bcf6852d15fd572e0062c8b1fc63a31312737f2347ea40b2117
                03f23f5811b7ddbca4a3f19fccaf4860773bcfb872000845166c61243b05b033
                4ce5faaac3ab1a7e950403b952e0b0ef4396e5f12abd08bb1354c28afb9bdbc3
                edb94b15aad3a85ba3444caa1019262ab6e04f343284bb5a21320440a95edb16
                d3317c875cf0187f0ed79b676ca45203c2d4a83233229f0861c58164b2b80b30
                0c13ce8899b0ba1e470ead23836fefb921ab6a472beb63b655a1238a5b8f039b
                f1c2916a44168e288d4c4aff2ec82c8189a7f7b70837e0a5756db0d38057c9d4
                5927418b9fc50048b6ae0f16a9b3e3399d9b08b67c41c84bbc5f8bb9b23f08ca
                cd742fa9e1225dc8e6cc32b86d6f57a3ac4b6d733a0655cfcc036c4b4c004a61
                1efd58035b06ba03b4a701a68f5945cd90bd4d69d702fb43f0ff10a5879ab709
                """)
        ),

        // Test Case #10
        // LMSigParameters.lms_sha256_m32_h15, LMOtsParameters.sha256_n32_w4
        // LMSigParameters.lms_sha256_m32_h10, LMOtsParameters.sha256_n32_w4
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000002
                00000007
                00000003
                c56e39882736881759e92ef7a37a1953322e3e9742a70e3f401e9bd35c973ace
                e06d7f77bd11b4a6082bbf7a5429dd4b"""),
            decode("""
                466f75722073636f726520616e6420736576656e2079656172732061676f206f
                757220666174686572732062726f7567687420666f727468206f6e2074686973
                20636f6e74696e656e742061206e6577206e6174696f6e2c20636f6e63656976
                656420696e206c6962657274792c20616e642064656469636174656420746f20
                7468652070726f706f736974696f6e207468617420616c6c206d656e20617265
                206372656174656420657175616c2e204e6f772077652061726520656e676167
                656420696e206120677265617420636976696c207761722c2074657374696e67
                20776865746865722074686174206e6174696f6e2c206f7220616e79206e6174
                696f6e20736f20636f6e63656976656420616e6420736f206465646963617465
                642c2063616e206c6f6e6720656e647572652e20576520617265206d6574206f
                6e206120677265617420626174746c656669656c64206f662074686174207761
                722e205765206861766520636f6d6520746f206465646963617465206120706f
                7274696f6e206f662074686174206669656c6420617320612066696e616c2072
                657374696e6720706c61636520666f722074686f73652077686f206865726520
                67617665207468656972206c6976657320746861742074686174206e6174696f
                6e206d69676874206c6976652e20497420697320616c746f6765746865722066
                697474696e6720616e642070726f70657220746861742077652073686f756c64
                20646f20746869732e2042757420696e2061206c61726765722073656e736520
                77652063616e6e6f742064656469636174652c2077652063616e6e6f7420636f
                6e736563726174652c2077652063616e6e6f742068616c6c6f77207468697320
                67726f756e642e20546865206272617665206d656e2c206c6976696e6720616e
                6420646561642c2077686f207374727567676c65642068657265206861766520
                636f6e73656372617465642069742c206661722061626f7665206f757220706f
                6f7220706f77657220746f20616464206f7220646574726163742e2054686520
                776f726c642077696c6c206c6974746c65206e6f74652c206e6f72206c6f6e67
                2072656d656d6265722c20776861742077652073617920686572652c20627574
                2069742063616e206e6576657220666f72676574207768617420746865792064
                696420686572652e20497420697320666f7220757320746865206c6976696e67
                2c207261746865722c20746f2062652064656469636174656420686572652074
                6f2074686520756e66696e697368656420776f726b2077686963682074686579
                2077686f20666f75676874206865726520686176652074687573206661722073
                6f206e6f626c7920616476616e6365642e204974206973207261746865720a0a
                """),
            decode("""
                00000001
                00000000
                00000003
                c94b744512be0c92aa45ab245dcdb53513877236830106ce43f09a2a6fb083b4
                53d15c7290b92cc2864d0780ae105d535de9f9651db43ea47f91a1334bc215e1
                eada1413a30a5917c0da8a5ae8256a0d40e98f33d6bca2d93579a44707a4cf0f
                6cab795d4e332685898ff004836490805f3b7eeb675c89b46f6d0d76467ffceb
                18f41176ce105214f4d57d1058aa1468cd1c2a894b62502b22ecf0b4f73f110b
                4b9726934786d58ad59b57ef1fea528421968ef136fd2ca7131c83ca9f03ece5
                a8c81765c769d79e8fa4093235c857d3e2864e2614be9535de95a09f60f2c033
                fe0125946b47bf09ecbc92123d486411c3c715f313b178818e00a81f02c8b7cf
                6c9f6babe65218f5caa8b0c38785fcc9dfe8c35b59077db5084c49f99fa7fccf
                5d3dca81c273f72a8f3e8777ffed035edcb9696f9a062a7befa59741abdd8952
                1dc585d1f8b37141c6d07974d768ec338ba71f98581fddc7db7f6b11602f2bce
                a4168f7959b621d7e298936a981e041092ebc13e944ee2150b91dee55a1f6724
                66fc58e24d8d1844237dbf4ae6d5623001afffe971174541fdb7bdf8695d8afd
                cc56ce99cd603ad02b700983e6b97b76b9d95715a57baaa5804dee2a02b88894
                0260b58e99c05f05bb5b2280e242a40d1f443f3a37ad051dfcf3e1888107c067
                a67379ba8716e167b862fcde469ab62a4d84162f68d74a6631ab0a7c41ba7519
                096b72110d7a9561eb8bed3d7a87b383a5085c99c54149b56d5d40c747ca28de
                ffc08f627609600d2e8f40bd7104bc99a2ec73952b857eda20475530c1fb969a
                c79c062c6a687bd2e008b8574260b822de26c9e7cbf32338348db43e4130026e
                c2dcd20b79e4b1766762037af058cdd89a9bb2ee4148d09653f0366c7e0b018a
                2b72a195a6b1c422e5adc23fbca25968f7bb97e51e01a83b7d1e83b22a118b76
                ecc8acac709b3e5de5cfba07d8e81b2dcac4a476359b1541adb24c2e9fd68a38
                32f2ae30b1db83470cb50a9f36777fb02b98f63818a078449de16c3108c0b724
                ce49b7f5f3beeee04ffdad3cd5cdd2e7955008d6e3123a42a46574a8237f2d54
                d54b3f8a6e01185287b167237ef736cef9881ed8478466124d72ac30a3caeb2a
                18fd68be71625289e05e076b7c290876dff1a95fab3503533be3ef0c5ad356c9
                df59630f3eb5fb6be37c61171243c0cf72ba2e2b83c4d84ef6ff9cac6b9790cf
                09eba6e0dc76664cf957c91b09b67f0e5c0f933d503c7a6a6be06f78019eaad0
                302e68f499e87d84627a7443bf1e2a8a2673247b1dc6aff3ac940d5f0e3a3ad7
                c888cb961d8adc05a85cf2c9af4ac80eb0109b7ae822933ddbfbbbb36e05bf24
                c3538c645e22946197d21091192cc2ece6f866fa2c37ce1f4a9be52428d23913
                3952f89286f88a7183ad17416360e0143d266f730cfdedd42437eb4baf9c3e7a
                1fe7a3e084c2ef4f50e2730ab9fe44c90b1d0167fbd1b6bde2f1457e2a7521be
                7bbd3bf2fc64dd111c185062afe4b90830747c68ad4ba890d691bd66d95fde6b
                ff7b1b1c4d13c69d4beb23ba90309fa17633ac5c6d3c0571530ee61e48eb4e65
                b2482bca9bb230d3fbb6a6e83145a8c918059befc05143a5145ffd4fffb38114
                e4026f262038e7d24d5f9dab453899daea13fb4bb07f7c7fa875ff4b8c1600bf
                d05be8a945f323243987db289a19bb29250a6a2d8d53c26e4cd5319324676f38
                a2adc2fbbedb324a5dd7ef804dfaaba33b4d7fbe17f134e1883400072d5f30ab
                86bbfdfdfacabc41b80749abb2271f903c71b383b5779aabb317db34ac01424c
                174cacff3dee16cf50e7ecb99d50afead31ab13363f49050463c25aea4d65235
                0797d491ebace80bfd2074095f1ab5dceea9866a62c1e7e3c7683c3d7bb50272
                741afac834a1e48ffe3e3dac6c168a33f40febfe4ede57637653c7731b4f5d81
                ac275de086acfb63133ae49b40cecf3e8a0333764957ecb1fbad30d80e0486d6
                5a9727a590494ef7aae57f40bb0d75b05fc8a99d98b28f2294585234894c3f58
                a1064bb824afa73938feb5b96b279dfd3f9145ccd4a8339d2be01a62ea25efbe
                b4bd970ba21c53d31fe66efba0450e8c5edf46c7116701b52954a11baf31727f
                7f845d9050a6a29759850af2d55d47be9646dfd6321af2677806335ecd6812e4
                a2ee84aff5aaea12478c36b822335db52a6cc0bac5b59c79f5d947fc526288d6
                585abd23a071c6a98bcc6909cc3e45b15ee4244f19fb5e91b9c408870a37741f
                bff097374f1da1ac712561ed5f908926f9c05748291de93690cc863d998640f0
                963a92ec0b5246745477c83c600e52fd937d4a190d621a114c185a0a84b5ff3e
                782113864ccce2b9acf86100bf188d9a00de08bfc9b1fdfffcc1f2ba68420bbd
                d6df840fb782a21cda9f051610663e8eb0d1e3e976886fbd98134101fabe1ba9
                33b2546f1a468697d453d94716b157dc8dbab7052424b095d3dd7a19ed709da2
                60d7ea9df24170f6feea4712c56f82158d1870b1380e7dc4313a4d419ece9ea1
                dfd7b3df4ede21f29e050156d5089bc0cff7e15c7983476e890b41f29a2bb373
                26dd2da928dca53655602886fd1c4a9c8153cbe50652741ba398857e37ad2231
                973c24653ab47e413ca95097a984e56f3fe7fcb3e8a7621d8cd710dcbf8faf0e
                2be05dd70a0e746a010fbabf8dc45963673f8e009cf146688852ff02eb1c00b0
                5c4adc1ebe2dfcd31a15f37a4ee935bad87bda3e27df9724a7b1564b3f251602
                50e722a50869d1cd39784b362bbee70c3a917f18bf92b77a5322e8b5c1a7d101
                77fb7a50f3d1b10669d570f7cfd4d5ca1401a4da432d4f68137986c456507611
                a050b0ae0b7b8ea295bf78ba9b8b3acceb14aafc82385928ee544ee068171a2a
                34a7e2e5b37a19a70c0251910c0966cc6b8a689cd6fc8a00f73959388624f9af
                ee57d316054eb23ff1aa63879e13519f416a8449942da592e727073bdccbfc72
                52baf02a65ec66bb9504199a147a3616ec20e7c57ab6a4f4c7def3eee13dfe48
                06372b85bf3d32cb6b3407a59af02c5549f29a76c73f43566b868e5f1092bfc9
                00000007
                a22f82a1ca76d4b8fce179e98c2b8f53fc36a1be0c8ecc5ac22a6c65b02101e1
                0da5ee071fa5a641198ad237ddc7b03f50612080ff3867ce11df78150d06c9be
                35bd7d0befbf473bae9f2f8e077e9358e8bf2b4ca45c7a6af889f56564a22470
                43b4b86db63819c4f9d024e3e09b96e731e315d10c3db8312e9bd33d477c318b
                3a5f886dc3d8595363de2e2d4053aef8b722f7fe4e092c1c9de6942c6597babb
                91ba0b88467321a8762daed53a5e31bd91a76085d559718fa3368aca9c5defbb
                6402193da3e293fd7d5df6c0d0b36e4696dbb0e6683dab1e29740b2625faf976
                a86910e194b46ca4857c3a1fa37b2a932ee300fd69ff2987fdb6e8ec7fb0a57e
                f50448510f72818c87f0a51be0796e20d9fabb66244ec5e99d6fa786b7524992
                bed245b61066371a4d0b3b91b64ec0e4e4912b5064f4e73a684de396f73b9531
                397506e10da845520a724a419003445e4b3a02ecd83a5fbfa392725054acc609
                b9d66d83ca4981ab5a826ac0c675706a0db4ea4f4ad55b3c3a9cad8ebdabcbb0
                c1526eeb2f678c0782ff98858241abd90104f0a89767cbfbc8584062ec2f7fd3
                ac911a09b2188c85a46b71ed13f71c740cb8f78513476f33b7157b49422e2710
                e4ea2efd66b147f1e94f512d5bf7ec40fbf020094c03626fc71053c6984d8b15
                00000006
                00000003
                6002a704915e03814410e7c4a86744b6269cc17650b33496e73414170f642ad0
                a5a308f58beacc5b39af6b988b906c8f
                00000000
                00000003
                4958d1fa4dce4ace05d93d530b008131b8c01191cb9c6248842e503676f89c91
                12431e2d9276492e1dd36fbdcbdced7dac7a4d86fc64cd8a1f6761d6072eb0fd
                a335e5a374b7139a62537af2d20747b199f32b97aa98b7ed722e651b4ea2e8b1
                6741525a4adf245446407ef3a0f8da6a374245bc5796fffad397d33ac887ef8a
                f1eef9ef53f9bf5ad79d0ca617c69bf8d5687551779762530a71c15a1a833cce
                ba629d85f03eb30b065fcf401282632e97cb505e41993ea121aabb93c5f5188a
                ae98a85213bfa98bdbe14d2b53fdbc4c00b1826ff9b34ce84293eee25a440622
                677e290239095d199a3776d2208ff1dbe2382657e83d1e29f8da4cad0dc2fefe
                2d17350229b945718f71d17c646c881229a4fa47e2dcf7485e9096b47c24ee1c
                4f051855cd19d787be09ea221bf827f6c2e648435f4d66d2028a011e8536dfc2
                66fe9bc6c4d342b95a692567291e6e2ddff81179eeec69fc135983fb550569c0
                4d90c040c2a3ecf2b81a55f5368ac0a7a3d9c2bfdb8aa13568c3147f56b0a504
                84c4a00b2b1c795e784498d4bd1951eb5733cab4a7b6103a4823f2a94f890c62
                dc83c57ac233902bda5440eaea0aa553e0528458b39e8b58ef98e157c95a723f
                7c074b5e3c4b2d2c15ded5582d2774c71173be7f7ecbdc659d8d5a132e39f64f
                4ae6a81fd550d8dcf7a1e695bc985d059892567fc502399123ee3f1131d113f2
                bdcbe027fbf6ef0738f78762982ab379547eee6b4276635d1961b7cfbf1bf6f2
                e9c9512fb4699468d882b382bfe6c479878824a52fd5bc0ffbc5cd857d1b96c4
                18ef6c59e799b93f14bfe5b769ee28aeee86ff5caae4e7cf19491a00f084b818
                dd61b0f97675c85077bee6ce5a44d9a47603491ba1d5e690f311037d7319bcd5
                74542337ed91eb7d65a01987d32f17b08d62c9b2cd326fff38e3a6e280c31255
                950b7911c262a17193bc9fbe9c43da2a3b69e61a5f09d67f667f64a12e91ab8b
                baf00cd7647b9fa8ab76057e5c2dbcb347c26ba18b03c25af8f1bfe18491f962
                b537cf3b24078dd8f45a2b3f707657592872d4c3c050bbb69dddc885399e068c
                c4d13709c8533f650e48780663b28218bc4e81ac3265b505f4d4ed6c8edf10cc
                c9db22b1f92029727cde22f4cade608fcc64372da7367886290828c478ab588b
                4473c2e9bfb975368fe6b53b71953eedc62980b60f3da18acbbd292b723d673b
                217d17366586344be60802ae9b4bd945c27e196317f9b8aa91299863a6dd336c
                9a321b7445934edcfaf6dc7f107a06ec6aacf3c4e7e04d83a938741912a9a34d
                6d6cc6598ad635c8299b4c5adb149f935f39a1cae255a8116e544306330ce49e
                6a46452ce0420c655ecaf659b8723780c9d2a7946f9b7781ca0ae983dc68c0cc
                afbaad6792d3b31b728668d91de0a7927780c78406521bcd483d3614d20a4265
                62c6435a924b998e3824351706be646e2d530a2389f6c09a2d8c443611e7c092
                330b56ef2672ad807f1ea8e249b0a15a57881048f91b00c4fb374201f8b92259
                739e521e10604eb307d3638090c0a4e4eefcda271a5387cebe0fd48d9a146232
                37b9380e40ef1108f00a980c4cf9abc88b3edbc7826c48508e02069ab34578f9
                db6bc3196dc806e0b1659577ff34d138e53cf784dfd03e3d8289b0d8fa9ab004
                dc35ead8fc1433b8c2b1265241f17d9629829c658513051ad7feea28e1d72a34
                05a2571a4af94b7ec6ab2ac4cbc810077aeed2237831eb42e303511a3b79475a
                9f9284f00b3baaed51ee50f3ec7af02effd2dcaf7abcb9e4bdd639c28293c8f0
                63d35b3f3934df46eb853a2b8f9e1e8e39abd9f22a7992325b9965ccc14e7910
                829b503007fe5a521c03ffb48f154d908a7f145988de3d35da8710e8b42cf0b3
                c9197a9d250dcb5ab5ac724e0eddca2f1e92baa61c2f2862087794fb6aadd837
                229c58b673f4a682b6f16178a8bffc71e696a29f9116cb896dfd05780afce77d
                cba573170724b067964901112baa343346d2c82e705b842e8be9e5b56577015b
                f86f9b27be99ae333bff689d93c7e4ba77be967cf8e6328284c76a2e1a3920a5
                515bdc7e5b65e6041aca2268ce13c17195274cb0461611421521e79a2d53af61
                1728b35b71db1b6424b6382aaceee899b495fbef4184911ed1877a3b17be2276
                571a2347d613ceee0c9de7e0d2f0bb5e18a63ef05f424b1ed1353409f926f84b
                4ebafa712f5ce1d36de03c6c90559a60f80e2b4915fa57d08222f6735a6201c9
                4931dc8a50f88b537031855c3824f617334f9ca13a9603ac1038d2cde3dab425
                e5a3ff1a7fb7a7c25b315187f202eb5311d078cb2e2ffcb1a509cedee7c372b9
                c06c6cee6b66b6bae7bcb158e4a74769805a88f5c9cb7c581e12e1d05ece3865
                4b5261501fe414ba395f446a846bf736e1eba29fb30fe80ee174677eaab90e1d
                eee16dec7a31c23865a38ee8c052a757abb91b104d1b5e0150e535b1c4669e7c
                c6b23f57a8dfed44b3f853049596b7f149e5d21be66ae72df95d1e969b054cc8
                49f2bc06a1d6bc5565bde1093648ad2581d691cad46237bb920deeb5facabe8e
                52e1e02d07903a17332198526a9214b6d4d4bac9f13994f6ae01ee74e673aefa
                5aa33ec67c4e0fec44bbd163fc6c317c0db4ce0de479115cd2a9474c7b222a81
                23cb5c784e3579ee3805c8a606d2c5d113fcd12bd83d03a9d72c8f72d6c577d9
                c891542cd3f3e59abaafdd2b0e9195f57e9c826039647506f0fdda125a1403f3
                f007d6db2555125c33de5b3a90ac9cd8485e48bf1db995708b697b5b47741108
                1b83dc7bdfdd8aa5a3506cf5a828c0e9803320af6a8b8949edf8fcbe65226beb
                aa2930c5502dc34b4ea9588ecf3e69edf41ec734be808b3e39c85a54a7eb5ce4
                33994c40209f02eef0e43adb560bdb26a969216b521812c6364bb336d004c56f
                91bc928887ae8deae2ea6bde44ddd39135222698c491759ff20dfb6d7686c284
                2ffd232c9544e3c39d37e9ce8bac185d58802050284a5fe653aa64e16c9b1777
                d011e2ab479a85d33e041423e6a50e5dae059eca94b6c99015e4b65047ae8c6d
                00000006
                d67b98d51d77fd23841bef1a34fd81e4c561c4f085b02c791553ed46c7644b51
                b6b97076d18c3ff64891f63784cd38c9fd1580656d4ed635023f4fca45bd49ad
                7958c3201c2233d8e1444a58b1f03221603f1c3b59b72eeac085fdccb79e07b8
                37451bf427cb58ac9234253ac0c56906b0540fe5bdf35b47b73f04f56b22c8b2
                1ff441f59983c2763a021e6d8f0ee5b73a16ef63d89168563b086698507fa56c
                ddd5d64bb968b0763191da92171580f9c404720254cad5c7d6900a4997fb0570
                bb9fcd4d249803c3ba2b3e5758cee761ba4c2df21f20ee2d36a547a0cfb216fc
                efcf58231cb7d6112ac2583fc1e52f3062c1cb8b14df921eb4b702eb703082db
                7ffe104cd0be40b96a04048def98caffea64e25ecfdd3566d3775200c5eb9182
                e9a45d41023db850048e05f200a4e7ed2e0b48c532e10c1628503d5b7f394cde
                """)
        ),

        // Test Case #11
        // LMSigParameters.lms_sha256_m32_h15, LMOtsParameters.sha256_n32_w4
        // LMSigParameters.lms_sha256_m32_h15, LMOtsParameters.sha256_n32_w4
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000002
                00000007
                00000003
                31b6c6a3b78feaefaf459a33e2acfa66a208240984abbe18996896c0eda7b999
                9d9786e59e41179854928ed5c5726bfb"""),
            decode("""
                466f75722073636f726520616e6420736576656e2079656172732061676f206f
                757220666174686572732062726f7567687420666f727468206f6e2074686973
                20636f6e74696e656e742061206e6577206e6174696f6e2c20636f6e63656976
                656420696e206c6962657274792c20616e642064656469636174656420746f20
                7468652070726f706f736974696f6e207468617420616c6c206d656e20617265
                206372656174656420657175616c2e204e6f772077652061726520656e676167
                656420696e206120677265617420636976696c207761722c2074657374696e67
                20776865746865722074686174206e6174696f6e2c206f7220616e79206e6174
                696f6e20736f20636f6e63656976656420616e6420736f206465646963617465
                642c2063616e206c6f6e6720656e647572652e20576520617265206d6574206f
                6e206120677265617420626174746c656669656c64206f662074686174207761
                722e205765206861766520636f6d6520746f206465646963617465206120706f
                7274696f6e206f662074686174206669656c6420617320612066696e616c2072
                657374696e6720706c61636520666f722074686f73652077686f206865726520
                67617665207468656972206c6976657320746861742074686174206e6174696f
                6e206d69676874206c6976652e20497420697320616c746f6765746865722066
                697474696e6720616e642070726f70657220746861742077652073686f756c64
                20646f20746869732e2042757420696e2061206c61726765722073656e736520
                77652063616e6e6f742064656469636174652c2077652063616e6e6f7420636f
                6e736563726174652c2077652063616e6e6f742068616c6c6f77207468697320
                67726f756e642e20546865206272617665206d656e2c206c6976696e6720616e
                6420646561642c2077686f207374727567676c65642068657265206861766520
                636f6e73656372617465642069742c206661722061626f7665206f757220706f
                6f7220706f77657220746f20616464206f7220646574726163742e2054686520
                776f726c642077696c6c206c6974746c65206e6f74652c206e6f72206c6f6e67
                2072656d656d6265722c20776861742077652073617920686572652c20627574
                2069742063616e206e6576657220666f72676574207768617420746865792064
                696420686572652e20497420697320666f7220757320746865206c6976696e67
                2c207261746865722c20746f2062652064656469636174656420686572652074
                6f2074686520756e66696e697368656420776f726b2077686963682074686579
                2077686f20666f75676874206865726520686176652074687573206661722073
                6f206e6f626c7920616476616e6365642e204974206973207261746865720a0a
                """),
            decode("""
                00000001
                00000000
                00000003
                a3e30c30ffb7b27a99e1e53b030fbbe5f901b7fc38b059190b9ccb9612503d10
                d0b0266063d5e234c985b0278428e147ca9686e3540e6d51964e27d0334c0043
                3564dc02fe8b1f911667561445bc9e61773d4ea4fb7b5c8e637ce482cfbb69e1
                15e118eb5dd28ad2db03ad6618af5a0a919aefafb46d8ae5ca011274032445c9
                7a49502e49064fb41fe28aa76c103f0d98edac1d71f51010d7e89d04028394a6
                87c98d1ba0b07470add461bdbc5dc553cb7225bc12d6925319253f50e8b8b1ad
                1d5ea6304ce5e69150b0b2953e38ba7bf75b5900e1b4e84e81d1ba177c7dad91
                956511d74a5b79c22aba2b821ec31021244fc4ceed9b1d992382e7f2e188eb68
                841b09301ead899c0857194939576e830cab8fede0f14eace0755d56d144eebf
                1eaf5f228f3c3a915de564ffd8c50b7a0e95834eba10737082d38f1c459f2a5b
                6479b129f77f57306bc9888a469ea7a7f69be2f4784c472d90b3a84c8a10789e
                5984c01ca390681ef94b483be22c2559222a3c8505055fdfcf3c1d5dbc22f342
                8d07178cb2736d930313812c30cb9fed3c083e3319208ed600d449df46106898
                1f7b3201a9f1760f388aff82eeea616ff89301ed98de1fc9d3a820c7b3e8d5e7
                e7a97f3eb819f4f5a08ef7e435d9df748d631539205204f269000d423bf195f8
                02091fc8f25e186da3ac3b63f7fcdf832c9a8b849a3efc207dbc39b7d6e67184
                4efa7ea995aba735be8d69ea2a7fa954f23d9cd548aae96068e4cca1e53f5fca
                2940cf78a43865ff1ef52ac1ed6c347a38b217537ebb6052a094ab8a7d5d67ca
                0ea8e46f10be7f07e4f5859f6f940c308aa156268a2378e36b6f642cda1a0e31
                dc3eac909426d8e9ad55ee21971c60f58571877e3c9e41bfec8c50c5775fb8da
                bc78c18abf1e9c3c9d6a1fdcbc42bb613e5019d95cbd12bbc820df6304240a7a
                0aae87df42881ab1e5df3906453ff32acc16a3bd5c01c4bfbe28240a05621899
                104994f21e87431596a2c43a336be790cc095ab7fea51a5f957caf3f221dd875
                59bad735356ed5364fd9f034ae53c1ae7b7a3672a5d7bc0974650923dca467c3
                0f826ae5bfaacfc381469b93825cde82e5f631936bfed860a786d35f29ee326b
                18e215c6e01630e811683da0d8d7b9fc24131da9de35a36d5e541896b79bbbe0
                eb15ade43e39134013b0de86731d17e7ec7c7cc1cb46d925c211e23f427ddc10
                a0d25b18c397a83b3be64eadf9106d5ca75286f599059b2cb0c3cfc892ae5e88
                6fb873326634a5231f555a4dabed9fd3370706c6e4af23e5736f5449883d446a
                593bbd6f36749f5a71a49175ba562855635511fe9005ab1fdbaa224f55b95a03
                cd08a215b8cb400a02896138900273216402241caff7398067b0f6c18edff0b3
                3b5e1350ee6157b2d79065ca0beb45feaa67795a821d783d3c566a0a130cd310
                ddad078aaa83a319ee6ffba73799bbe3f14a406be25b3f989c316256ae0120ef
                bf3593dcbfde8d1f1dce1fbb1cb1c6c8258623112a05b70389b1e801e17fb17e
                f10544099ac788eb4cf045ed1fdb9e5e957d7fd930bd927f4c15c253b145f848
                dd1ac985fee170307f56d34dae9926d67d1f97fa014fa3ad57c10d76d27dfcc0
                0a088a043021d5b053b6d69e912d3cb85b7c2cc029ecec40089333c7c9ec45b4
                f9c3ec3720df8fb592f5c71d7dc230f90439ec1ae12643a8f4a30dc96e798cba
                269bcd463116dd9657c89c569b519cd061816150e8c39ec3e798574610cd13da
                83b52042a890b37d10797ad80f74928a96b697bc810867a4e0e96e98585bf386
                d0df41627d76af70436258dd3ed1ae49554d8dc5a2c7e54d477af70e2a407747
                227fdd0bc0b5a9019c4df9fe95dcd8ea6251ef1bac071771d364fd428c3db3cf
                cb4eb26b7908ed96d30eceffbe634c0184e8cc2b807376d7d5fd9f4d9579f148
                8f085df80f032c6c382a48dc80b07bdeca6085aa59f97abc666c1a73d4aeee34
                a35e3569ac357a84fa1a9fcd84af37b388dae14e03be6b5e088891060bd69e5f
                419fd7d2ae50eb97bfb24c7dbd602de5db1f05c84f1f47fc7c6d40c0fa4c95d0
                464a18d0b6338ce123b46188492df786abd9075a535a38de4b42d8d8bd6691d8
                fa9dcd8af407d5f20a59cb959802735c2149c18a067367ab9e457e93e569ec1c
                42741ac055420fd531dda33faa9f179e272faa5b32c1e6d3029b55e21140f4f0
                104a89760e5e038c07a770bc85726fc57159089cc3a2ca2b8a14e9f040b41e30
                3e2c57f64dd7579eb094e8deb536a45bfc2840428617117a1b2ae8259764e860
                efc03fadfca03511a370256420fbda932a5ab58c98d0df2a9aabeaa7d886ccd1
                59281e77e7a1c70094722ec788212885674e90a3134c2471c399bef65cece979
                0ef61cf83201cc137a09115b0fa06ebed4de9104974f80fe00b54367ab267483
                35e0f8fc02d52e4c556ab748a98aa161d4c9cb712c88155b0177699f5fc4d037
                02560279c2be7db70603f03c0c2ccac7437597c79da01ef70d84ae5ab2e0ecc5
                b77e9f6a4c412569589d97bfda88efdffb626dafd24408eb79919cf48a619744
                fe4c42f3c49ec4c1f94ec8e79366f036aa1366ad559553d102efb9b3a913bb06
                feca31ab71949e4aff7fe00be4bfa36e7d6dedfd349c0bd0d8a895b7f8fc55eb
                09933f061f0c4a756caea8790d134c58cd1469ce78edd16be26e0208d449556f
                b02f343493fafaf615585320902dc4c606d3ae2fd4df7619817b49d47a672121
                c18da014aa89309814f49d2259540aa4897cccade9eb94351950a21ef3aab45b
                90caa42e3e41f39e1da7b341cacca50a8978f1dab0a54becc79c9501c9ad7c06
                db29ca593441ea36ce2d48aa5ab2455efdd70e558ad9f208c2f26ce7c716ef96
                44a12f904f4cecae04cb68a433db2d3aefc192009355e6f67f7b565c746c42d7
                ed6645ca837351fcc9534c66451e8e1fbc327999423aefe4f5f620c8e15000d3
                2c621ba53d572da4d4871d36173e83b1550cfa40a3a8fd7248d351a703389122
                a4b070ee87b44ec6543adea6921c567be95a8034a06a00c3fc102b8d2fa4e6b8
                00000007
                c792abc23c442a9822041aa5889816e9e127ab2323bdf2636cc672b47d055dac
                698074f11a96c53449a1ee30e1bc27fc8be61d85f4e5738725457d4cda5f7d62
                fc2e949005146d26590fd090a3c50e4141c63b8cfb14087affe86e9ad5207b4e
                dedd578be3e0a969d10fcafd1ddb134093d026a9055615e07ec4419786a82aef
                0afd2ee68e706169d7688ecfa37c8533d9f7af2bbb02adf614c680db745af017
                1676255f9e319f00501c43e4787e8349c9b04bf6bb3e7763fe6e40d6c21e6ec4
                d4611c5480aa2f8ef0f6ce3208748b966eb5d1c5945c54f8421ea3c2bf7602ce
                c1dbfc1c8408f84cbc3a2b7932764baf9c45e60404f60736cdcb8bd3397398b9
                c0e64e4555efd2c94f04a87ae037fd685591599d5c0b1a7197b6886df17c5225
                5ad8fec5950668c3dedefbe53ce5ede382633962be26da09260ee48b5d8ba67f
                900b54dda5630094a9fa85ae4217faf60801fd315f1f0dd58d30190fee4ef279
                51fc2c5633cb46f70e9ee9678e27ac8482a2e48415b14e5d1d3bfa6a86d5b972
                93f2d0a32d0c567002b03df6cc7a4c36129ecd04f9cdbc53cee787a344f305a7
                b1e61609f7df2745c75a88e07f071d1e93c868409b67afb114cdd539562046fb
                7d85f590ebe6969684eeaf7b42547139b50d64400bfe76c687658bf7195c9791
                00000007
                00000003
                052aa96293117df09356c119dd4b9edfe3d7c53d62d9855912682d1c502d64f2
                3b298883ffe67153ae1a23d0c6ca41bb
                00000000
                00000003
                9058ddc450f6e1e7b9ddf5fc65edb6c55d60cfaa3b3b1e62fd53d7286903b9c0
                63656485ed67b07407c84e1c4a34841d6e88aa0d6a22b262da4265063f3a84c6
                fabdc96e1c523d68043290d7d689e7bbd572880f0fb2a1fc2d247c622faad758
                ccf2f8751b983a86c29d721002f14050150227e594ab05b2f6ef605140511290
                dc213ca6db06bed09f33baaad9a0eb840c7b510bab2a31f7bc039f944b5b40a8
                1f047eb423d306a8a190dc1023c6433f39edfe68f2a11357e70356703d15a46d
                6df90861e10f84cbffb6efd1b3917e38f6baa2602f4a3d1b4bcf6ff6fc61e22b
                edccb0831a655474d5d701fce378142a307819d5a1e8156d655276f1f0b7d9f6
                a250c938935f86a2f12410cd5756526d70bfb17e0f90fb440e3e8c760b693ce0
                5c5cf7af31049ea0144f6ccfa9a2ca892df28504391c3f1e7fa053fdd29c9767
                94ee174f23267131aa62d5346150d9f5ed14867d86b55643ba6ff94b8065a85b
                a33499a09fc620e81a57a5d059e37458801464aa461f07ecb15cbd0239272919
                aaecec880c2f5bcc5c73af198324d06d9ec48d8a75a0d7aae6fa704bee98eb62
                77204af01e13cd80bb8ca5f243c67b02d2eb535041b0b41713c155fc5d5d2ea3
                61f27770cf31264a034989896edab335be1ec9b2c5df0106093162af112d1d7a
                9913a2edcb1c2fdf732b25c003ebd9df617cc5fc47d8413c2b3b34dd542a217c
                32e4aa7a283809f8011dcf59b639e4ca7baeb9b408a100188bd719446c77964d
                4865d6b138e9ef1c76d13145218c7de1827ef3599f8c9883ffc2d2412e48ef13
                4ef821e1d426748803ddcf325df9b49cd61257f2b2d3fe2bdc960b5140f5ddb6
                4696063fcaa699de790947a4ddbc8fdabd46f45d2640a07b434e304d00fa8310
                8667fddc1e04a671548ba468ab83dcc897ffc4b776d1694618ab131118cb7830
                ec1cf9e432d8f04cf48d8485d58c51b18d5a0bd795588bbcdf65a583f7615e32
                4d4a76cd5bfbfec3a0c37c97f9b5b1c5d8795e3149fb11096ab600cd9f77ab37
                0fd5970b641fefcf83870fea3d7deb9a4b0d4cc53a9d28330c07802d52635ad4
                3cb76a04b6fc09a7129da5a3d617935d144ca865c4a958d4592dd9dff080a696
                b2c7301fd3d2974df382562eaab239df41289c7a2b9b55264a879b322d693acb
                52c38d9a31559fae7592bc29b1a83e35caef47d793fdab943ff2e3f98a898c80
                73ef087f8d4419f1aebd705d2e0ae8fd137f7a46912c6a0ab7cf33f76d523cf4
                d27e5c2a22783974134846d923d8d966041d88f113451b439db3af3c90747739
                aba2de8ee7e1a7f1f84c6826cd405404c838eb387cba680b5c8f62ce8376c23d
                d7bf26386855df8f8d554445987bbee37597171f7849590db96281fba0cd9055
                92980b85e0f29f57cc6536e2172695607dee4f53fab7b123a34a97e12182e13c
                623f3ac68807718ec5d7806072dcbbf087e6f1e5b834cbbf7d8e85c3021213ae
                4100af501e8f77f57fd68c7b339fd649357b396a45eb2826fe06373928cbfe51
                f1de9fae833492e42f82d01ea9da9f4074ff22f5b59760d776241c73ebb0f05d
                6856dc9e07a2af219e38998afa04ac953dbeb0672b13277bca76ab02566d3f42
                2c6763dac87898373b69a1fddd92ef198f52388644be117fd8192b592325039f
                aac41b147a73162a5e297f82e09604eab0e06866450f8cebdffa2ff475e83daf
                e5eb9296626a895518872a97608ffe04f454a9e0098add3bf581f53490a1e2da
                324ee87fd47b7ed7551c0f222f475a57fd041b6e626979434d119525da68fbbc
                1e23d8affef88ad8542bd99f981c29435a3a7f3b40ad86426c6bf0a0948a15f7
                d8dcf09f4d083cc3c3ac77191afea39e3852f2b679bf2b3028a551e91bb41272
                bf59c1bd443dd6c71ead77700dfcdc2a9c686859b12ecfc1f75cddef50ed5eec
                fff75007bb26c09b836c32950104dd417ac60622bc57df8e55815204a75d9c79
                c13cb605dbc321cfbacdcdebb24ed3fbcde6d7d5b667262a89346598d9f2d9c1
                6e6f6886a55caf87221dc27a911cf4c389503d7715b1a791bdc17ba5bffe862d
                138b52768c1608f356dd8c88d5b28903a2295891fa139e4cfb16dd22ac8a755b
                d496864c95288aa4197ceaa54112e60ae999a8aeca4bef48602298b906c673bd
                93bb62da2d0cc7dd0951d304fb878513d2ed70ae39d1f56bb2c665281551ab74
                0c1976060bb67bd898868a2c245747a58ea6c1e71eeca9d1e196024b8df0314a
                96c278e9387e817caafd11c0cbd517b7cd3b1f1ca9c38a5741b7a46cbe180a9a
                deca3d975759eeb165354ca2ac5aa5bbd15841673f5aa25313009e570dc1e70f
                58d5d5eea6b10c36f0d5d09fd02021ab10bad7ea9dbd04ed909c7f1b6632eb03
                827d67ee609b96079824394bcb3e8f99d7ad77e27c5970fdf56f33097cd38c11
                1a095a7cf2ed76014b32a5a73e6a4bbfd86755d2b6e403671fe65ee3fbdf5c30
                3b261e3bc613468a46a8c04fb761fcf8b262b142ee4fdcee6a1a8c7baa76f442
                2880206b06d406ca77dbf75bf5c0885405e9446e6d0cc8251c11b2a9bf12900d
                a45b2515e240afa6d6091cc0618125839efd0e0cf55368b10f9ca04d9e18830a
                c0992ca32b1923c1b950215e3eadae1e3fd102f64cb7edf371fdf743ed553048
                7e7cc35827d94b7a2462fcdcc7e6eeacbf3626498d66ea1e7d98118431c7b823
                5ff390bc68d3063cd42bb63daf5db50edecfb9ce8a83cc5565f1d3ea2d843df8
                b899deca8a36e07b79a38be4c064af92b908658743c2808b94f8113490163669
                4b50d40b246e3f87930684ebfa0feee96a150e74d3e9150619ffa8c12db5d951
                2e55dbd4dc21e09d0262a3b4c9ebe995ca12941e0bc32091891c68614df8ef12
                bf34b0441c2204694236631cf4b1992daf2f4511f9f3adc29858dbe1100bfa09
                1a8343cf5987fd8175ede1a11e6a5e419d48d1fc834cb83e71e2d2b16a4d724b
                0768682f72e83797757e92a04fe738db302cc39622b33ff37a6f3f39503bf229
                51af13e28a4f1210f8b88c5279464dcfbfcfb8c2cff3d2ff11321b7f5f1e188c
                00000007
                ba78a4b874ece30e24b271f4f1a44db792a841a7405e270accf650a793961fd2
                09499109aa03394808cab26bf07cb44a856b76a52384c2c8cccf3e343a724d4c
                203d131c29a5060c9d518b60ecc1754c52c7abf223683192a40a6831bed6dff0
                80a26b30e52b268c8a9184857153af60266b0eb40b0bc255d0e96649c63ec359
                6686c1f11ac84488c6ba20f76649b257b6a3bc60d24d6cece4f8396716455dbd
                9b9df1ad3ddcd6db59e41d67d52d6648b5da92929d61a17e5b8c9b46246c5101
                ef8f4e1b64c8fd471deab9356fb1f5679ae5ac71059a1d54f7a1d72705128c50
                b45902f8da6e82e2851bbcd51d8dbb8f824a222596648dffbd61a1d6c95f9885
                68500d1d3b084594e0a5c3f3150ed15c4ddf484f45f1a615d63be1d7aebca110
                e80efb7a86132d6a57d296343d7c784f50859c9072fdd30c08d71bff9667caab
                65b044ecf87e90d7943acb7b5f26a26562ebb6b0a2aa31514c096125ea73c2a5
                d17cb68392d92e6c1ac7805a57466a3738256bf8de6cb3c5ee944f45bd4f1d6a
                d9977f46826deab2abf93378819376fdc7b61cf344d2265b9f8cd22a1632f738
                244569171a23d6d593bd19634758b7ff9c8731720e771023fdb0a6241dda4f61
                a4385d3b9c5b6f6bb018324528aff429eca9c1264de9ea434a1a90e07f69015e
                """)
        ),

        // Test Case #12
        // LMSigParameters.lms_sha256_m32_h20, LMOtsParameters.sha256_n32_w4
        // LMSigParameters.lms_sha256_m32_h10, LMOtsParameters.sha256_n32_w4
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000002
                00000008
                00000003
                4f9fbdfc21ece22a13965cd32027f6d4e5706e751440d214da485f202309a24c
                f90dafc3d8f09f797b1b6cfa3636e18c"""),
            decode("""
                466f75722073636f726520616e6420736576656e2079656172732061676f206f
                757220666174686572732062726f7567687420666f727468206f6e2074686973
                20636f6e74696e656e742061206e6577206e6174696f6e2c20636f6e63656976
                656420696e206c6962657274792c20616e642064656469636174656420746f20
                7468652070726f706f736974696f6e207468617420616c6c206d656e20617265
                206372656174656420657175616c2e204e6f772077652061726520656e676167
                656420696e206120677265617420636976696c207761722c2074657374696e67
                20776865746865722074686174206e6174696f6e2c206f7220616e79206e6174
                696f6e20736f20636f6e63656976656420616e6420736f206465646963617465
                642c2063616e206c6f6e6720656e647572652e20576520617265206d6574206f
                6e206120677265617420626174746c656669656c64206f662074686174207761
                722e205765206861766520636f6d6520746f206465646963617465206120706f
                7274696f6e206f662074686174206669656c6420617320612066696e616c2072
                657374696e6720706c61636520666f722074686f73652077686f206865726520
                67617665207468656972206c6976657320746861742074686174206e6174696f
                6e206d69676874206c6976652e20497420697320616c746f6765746865722066
                697474696e6720616e642070726f70657220746861742077652073686f756c64
                20646f20746869732e2042757420696e2061206c61726765722073656e736520
                77652063616e6e6f742064656469636174652c2077652063616e6e6f7420636f
                6e736563726174652c2077652063616e6e6f742068616c6c6f77207468697320
                67726f756e642e20546865206272617665206d656e2c206c6976696e6720616e
                6420646561642c2077686f207374727567676c65642068657265206861766520
                636f6e73656372617465642069742c206661722061626f7665206f757220706f
                6f7220706f77657220746f20616464206f7220646574726163742e2054686520
                776f726c642077696c6c206c6974746c65206e6f74652c206e6f72206c6f6e67
                2072656d656d6265722c20776861742077652073617920686572652c20627574
                2069742063616e206e6576657220666f72676574207768617420746865792064
                696420686572652e20497420697320666f7220757320746865206c6976696e67
                2c207261746865722c20746f2062652064656469636174656420686572652074
                6f2074686520756e66696e697368656420776f726b2077686963682074686579
                2077686f20666f75676874206865726520686176652074687573206661722073
                6f206e6f626c7920616476616e6365642e204974206973207261746865720a0a
                """),
            decode("""
                00000001
                00000000
                00000003
                7d5dda179d7f33bd79840ecd9c46c0274f93453448b4b3c8eee74440b900b7e9
                9da9eaa94691fda7716fdf2354ad1858df27d29b79c91cf30423c32d5e093b71
                b0a141ae160487d8a4451622b5ff1488167cbbf342a876d53671eb0272c6d1b2
                ab8d8fe4b51d50ecaa977f379903c9dab520df31beba6f6dd2a310b8514d7a3e
                c78e2fd0a33369da3d5e8fde5a3df3669474ada32db4854eeddccb9daa82270c
                9f1f9d7d0f45d6b59db36355ccdfff8a08ce7c16cc5c15066b86fa955c7036fd
                5706b8560fd06fc1612fbd2aa9866eb40d962bd12585885321fef67dd80c3efc
                c0a9f620276038b0850e9598729efcec780d56a67f98d1392426308adfb4d8fb
                e4128ad38aad50060186b40fd63eae216d8761ad4b66600f90ad39dbac2c4771
                6986754a8c505c0d33906b8a66ca740f68edf3ea55c9e0df72e7f5f1c3b6fdd3
                67d8b0205a880a3ac1c9d2a9e48616753a0b9b34d63a8ee4b7f908cbd0a8eebd
                a55823c0abeb1911d369ec16414ff39a2d1068ae1331af1eceb6ffdebd3bbc6e
                e9ce74dbad7e6fcb362d111a9992b875d4f993a413348d91d7f217c0239b52f6
                0140cf51777848ddd04fd1b481033b9ebcd8e4b129db0bb801d99d05ac2490e8
                1f858d6f35ff29abd35c40fb650f97a0b32061aa7983822868119dca5c2c78cf
                a4f76d26ba91a56bff8c6e500fa35aaf89cce3ca8dc5c9de98a89981cd9283ba
                ceaef88dfd5a177d2c71fc7c1131c6ce74be3a2a7a673acaa731482cc4bd4dd5
                8f0de26586c2a8863bd96d505821b7e78f88a0560860d022961e6d0ed3cbe9d1
                22dd27f77208ad85d12dc33ad819e394841ff0055ce1649b20cd00af1942c76d
                14cb6b645d459caa7707f081f47b7a327b3596ab1e3af5b741e503f4888f5261
                83d0607e274c4b544e72417aa98326de9bbf51b035bc70e75211b084a947119d
                d7929a16d43cba2663a8d2ff8934ac0ee157f01c13fad772bdf15c52bc1cfed0
                24aad7e06e3fb72f7ba7bd03860b804ebbf3a78b40cfdc46c147a393d8f88ed3
                f921398995697a51b5e3cd8a3b363d03f60f531e3f2d7a8205bdaa4e652f8dfa
                42a17bf406580fc7d2240314bc3632850352a9212fbb56e2c4055634ddf76f30
                3c9818d2e806b3474587c3dd95d871b7b7b9987108d837efdbafc10735285f3f
                3613cffa7ed61e402e08cae00dd43aae21cb70c03cf1fd0313c365075a2927df
                a2af481b3026b81aaba7652b8c27a1bf64df4b079ddabc4e142678e86021cfe8
                35347ad78e3877a35bfa9da1325935bfd8ee76437c95e95a9199db1949a1d034
                2ad2e61b7459e9cc67832093240540d8c1c69af238f5ff9ac49829c5a06db24c
                a0cea539f2c58a52e256f404bf0123d03979c32da4b35847f01b253105d71a87
                86972babf78052003d2f1183c5bb1f986de6b443ad2b8674a8a88cc6dd1c658d
                4efa74da693e5194a7c8eb68a7ea8313e7d42ace9ab5f15e2c536eb4c0da7579
                5e50ff72b351c773c603cb749d6410608fe64e191715276fb36a97ee81720d98
                06209e53f728bb3be9021a5ac2aa157616b6c9f4f587916732cd682359c8b616
                84d3f0ad309c03a3d168f3927601544c622b99a3ddf5291e2500126278122794
                5dd8244314b972e0e6c303089055a449aceeaad870a1171b3a93fb329cd777a8
                9768b54671a3e87c9861d2c4fbd68971da747e0e12a70211574f5cbd8c98df42
                c7439886d089c3515f30ff5c5fb9e1e5a7f0d901ef76a8b5b290e0236caea5b1
                bef9f583c7d7b07bf793fabf560ebda31d56a19707fec2a476842dc8171faf3e
                b572452a092de32ce195160efe384e7f5c6c5ccfd3201dc34d33fed0fa0fad24
                e13053dea01c5ef210e1d5c2d77a94a6acc3fc7d2bb7ead6ef69c5877c1d1587
                931fef54754681e96904f5c24050b8d7617843674f4b652bbbcd19860da98aac
                c1e00d9c6cdf4a209c8b530b243b7b8f1646b55f9c4b0a35b0133c67eb16b7c9
                05834f2e392299ca1c61863c89e333f4530c25c7765379b69fe8ef30a644e746
                f09ad8a6090c2548e9294c1ea78695b90639e20de1fb47d3f378689254780b7d
                cd4bd7dbc66b62976a5e5df5fe72eda740fd4e38382283f1b335ed72000f8e30
                c0de5eb9256808cc5082611a10395710c17861bb7513e52ceb23d005bfb419da
                440025643128f9d074351a7ac35e92e9d9eb1520eecc78fd7a8de4919d428642
                e1641c269261d4eebc817c82d5b59b3e34bad0a081aa549c02ad7c7c1e8b936f
                51eeb581e9a27104a1d210b49c8882234b2de3a655ca4b69b3f72bda9e910875
                8cf881ffcab37b11ccd7991b3e12493f4a9e237c3184a838f029be09ac3d511d
                12dd84068c476afcf6d18c9516ae5800547b26d0a7d366af96125775f5fda02b
                9ac818f4d2209610d48e86c1f57bc71e31519363ea8bcdae791a88b50a84420d
                020b627b80424dd377577f0665503dad7d16d2f7532ac51b7adfe5704592dc76
                dbc6a32081f3435723b51a7c61ab2b8ffd27179561a8debafff46f1f6ba6a7d9
                79a12962352728770024f016cc28caa3de3e7d9695c3c0d2c53d45b7b57e797a
                10d1b64e37fa8dd2981d010e95fa9c75619606aac70dacc633d7b415f440d4b7
                37fe944f936990afa5f0149776483ead9759af9984c0d37a710735854f526e9c
                fcc200c16bc62b6c0a7d3c18189f721eba9b90a29fca15de8d084e44c97cff27
                f28af689a7670286687cdd40e60a9f68e7eca10568d5a02561c3f437afc57ad6
                9dd6505b04690530c9ed30c5278299a58057b7a24581a0eac0d26166d3f6b49d
                796f516103f61ec8f5a6e4307f97b77fed8898190b744c66eb661706fa71606d
                5692cb08973738cc0ea9a247590f23dfa8f2c0d406da850c8fcc639b0070c2ca
                d9d4c42a42255611b781104068ae3d09e94a7b2b46df774eba4dc6e712509a28
                2735e898bef0675f988568bbfdcd09720fa6e2afdc5590ecc9676faf12986f10
                9eb838e4e998968dced593c337607f6190b1f32a3cf5aefda5043cbc57e0ac09
                eed1660ccaac44af2c55b12ab13a7ef3763eed79cd5ab1b754cf15883c0ce966
                0000000856bbc3dc1996d6154a8847e9800276e50ab54db3c75f879f03c48c91
                7cfad26d2199acb69cccc8149332d3fcb94f076e55818aa59dc477ecf1bd16c8
                f362695da854c55030e90919b40f9aad3d43ead32e97f596c066eafe89d4a8b0
                d228e9ee47b531dcf1337b4e45c4ad21ceda14ee10f5f60ad341ba84bda14500
                f4c6a8f5317bd98da8713f1d76508046c62decaf44c61d752a9ddbb539eb5844
                b4ce4a5ddbc1a050472236a437713603e34fa0eafd713017b932814b6b24a74a
                1ff7a6c5d47b4153e2007d87dcaf47573425647f7e6f5f032a934967ada85b7a
                8e8934b8aa5395988a6fa50261b8ce6b7900bd6a8bba2d2ac54cf7ce04a0718a
                5eccf4551ae37c7b136081189eedbc1793ae7dbf368361f00b1df0cf236bad37
                b87b7cec97715aa6daabdb2b30301c376ba7ebb44cd9e2366411aa3b3a1a351b
                46c45cafd23213e5c6aabfe59d622904bcaa84d90fa9da47d522bef92a523a84
                28e1fec7cac1137532af968d413345dc3ef38b7d6e5f83594f31570ce12c3d8a
                2a046604ec433ae94cdf82eddd28fcf603fe605007420fbf34c917e7104fe1ad
                1ad45d346c2ac6058bed0f4c44d003e093e4e686a9dd4d80abbb716f7363f204
                ede2d0aadbf882db09bc75284fc29b6208b3b8d7110cf847e1cd9a1e04d14cfd
                23accde55a2975b5537614fe1809bfa01783c0e3bdc821eae70a5e4ddcf66b4a
                c27a3d76e12189a03eab3cd40feb892257213b1318bed0efeac00396546f314a
                97b9afc7eb3ef845b4d53bc8593184d734ec19c93fcc0431c5d74afc8f9eebae
                f12c7f186a464b0d1b6bdf7ebba592ef9f6d3b3af332a7f100f82619535d7ca1
                cd99e4134b50e84ea167948c244f928304f6d008bb7f8a10b218f46c227f5255
                980ea31a
                00000006
                00000003
                5490b78c72751e0485c63a1bea5313f0462907f953a8d3ce7e585426c07e608e
                33cfd90fa196727a43edb3759b712afb
                00000000
                00000003
                0cb7dce4975f968cae1c1affad81ef53fc2d68bed7d3c9bc83d85204a209e24b
                d73256ce79a2e8d2d0ba728a526248518d5201250ea5f831a58c2d03e7aa2fd5
                f4eb1081f5e8a7b49ab360caa46395d5c48a527cc84a2db4476194c13e6cbfa8
                c11e825f438552014a816e147410daa4ee0ed43c696e78c704f36e924a76008e
                e231372d7f50586276da32b3b340d5714d3d1ad1e12e12de00d0a67c61fb6226
                5b5a22baf87d2f22d9cbd79b2bc7c9cb0f90a034dd861f50cde7b41ba64df79d
                b6c288947156685454739621b526565851d66ad7a47929a97bf82932664db663
                550b4cea602c3afd0691cb363df0f8f1858d7296bc99d490df0ca5b5bc5d2a69
                ec20f1f85634b7a87b82b4d293d6996fa02678af04e42801871aa56138465452
                7f699809d424f62b3d0e4a4e106c3dec086854ba3f61d0edb799612df24ea4a6
                a07a7811abd4968500e344948d1293baf4d6a194905d5c526b16e4c4ff28bfb9
                b9e03d7a9f184c06441b0631f781f56d5a51f62f3fbc7c3769e32f68d623f406
                94fe7179b89289c24493041f4e2c653173cf3a8b2051453d31422bdd50af4a81
                2e7bdf63d04765a1ea6a397797a827b7c5ac38d58a7f6196ba5db478dad6c031
                143f7684b771a4fb7999152a69106c72642742d892233147e085f5e4793850bf
                a83660db5b53df4e67edbc37b4a6a076c419e02ed54b923d708f16d8561c5abb
                a10fde6c6043f88137bf80672273924336a81be63d44ec26a4afa761164a0c09
                640b1aff3ba0f4dc43d16b382d1135a57936a150323ea3587717a3dd0a8111b0
                0b875aed7c7be5250fd2ef9bfb968293ca8121d227efb07b8dff9dc819e6a5f7
                459fa5ea01f62b2a8e278e9ef2a7029b08df3a76198de1e840ddc15533195403
                197be0c8d1a086651e4dc09e3c59f4b3352662800af531e927b9e52e8a1d0631
                1df831a487760cfd6e7a713bdfb2a9326bea28f59067a989d1189889b8a6b9c4
                011f0d28113d9f6c2e506bd120bfc78223f6415cd657391dc3f9259e5d9f44b5
                4eae3ffbe634eb9880c16f6d67db25f0881fd59e5796c9152efc63c7cfd4e3df
                95b98f5aa6a9bce0615a34bf15114dd65e0f44ecc7835754ad6ddddc0f2b69f9
                2c41470fb6dbfcfdd1b61292b353665c6fe8601e120ce412bd643049802e3903
                f25504f80be1778e24d9166545e2da6552d4c409093a781e33447179fc260164
                8598234c932642aa4c307da2670c70fb4dbba29a4aa21fd2ff302364cb13bced
                84c0da69d60ce846095ef042f7213be8ff94c23b12e5e5f7bf4818c461355383
                c6fbb50e0988f992d82f75db4b98246aa6e6d50713c3b436add3b95670df646f
                ebe742cf69c6970b93c7acc86a868c979980e2dc313afcaa67d31ce5dd0c5d97
                b20f6fbaeaae2a1b2126e259c2d0b3268eaf96a6a42e3eb0dafb9780ec17c01c
                40a85efc413336bcae40324924a311b8645e6554004ac20d4c6334a505ae5c7b
                abec7871ee593d9ca8cfd9760417966892d916d9125a81822f96d3d3fb5ed343
                a6a8ac47a863dd3bdf502d9d5ffa040398788c446037b82e00392a00ebcc7c7b
                d81e85e5986422735ff38cb189dc7c24510a7287714d58aa7b3e645a9cd5bd44
                fe4a3573844f5df0ec015c83a15eb3c483b2570de8d105a1fdf6dea1edebd26a
                1b4303814b30f16bd064051d077f07e97e825372ed93a10d2022d2a8d93c085d
                862ad6006364ab5742fd3e3451ff62c1e8c966d200e18f3f55d79d1ea49d62ee
                cd2e2a6202cda533761451e34b691d45b5a6b2227856dfe27cfe777aa9069717
                3341aaa79f07975b73cf8c5a26baffaa867f1d41e7d866895f1ac652c19db1df
                3124d5d89668032c62c6862aefe101158705e3c735bb1f211eb5faccb167a11f
                5f2e043d8c7ae469a28759f25cb477fbd237c44a33a74dd89723aae046ae56a0
                b15d415184f0bdd947961a7d3e66480cc3b51d0ca2c1db86c327b0d3270ba864
                ed76406b22dc6d107bc172fb9df3513e07ca6365389e41f4a746f56240b921fc
                464d1b28d047026abdf970f2767e114da3ff0355a9ab0932f3a9a3097520f9e9
                3bf3b03e9b460a7846afe5f316188fe17751857d3bb43ea3a506c974f5d0270a
                e519d5ad24b407372f0323730bedcd660f9abb0acc1221ca9e2b0259b1cf4ed8
                c64b74776399928598a216ce6ef437056bb752d437347978369b7a578bc41077
                d57a6aec7258aabd4c623c03b1c03f5c73931e675667d0c10970f5c620fdaf4f
                80d59b70908b30c7a40114d67e92eb93ea34944fd12d6cc7df10bc0d1227999a
                200d3a9e92a01a8736ee839d0e72e59a709581e58c6bb03ff7585171f534b97c
                4a88937cb7c6a6bb9b1572fd3c2312057ff5986fbb4119a440600496497a91d3
                ab0e2b5a744eab30325471539fa9116fae25fc74fd17eb1b75329850ceeefc05
                20fc7dbbb35a679cc0c813a19abd5a5730d5b9f0c0bc08d2bbcaffaca6e7b670
                1b9f5dcced66b888e93f27c1586e3eb3637e75664f8b88fa2ea8408cf5b90a0d
                c9fe016cb773fc85e0b4c5e37a4dd9320fed177e0718862be9a1535274b70668
                eeae69369e3746dbea3e3b459f4d20d4fdfc534d5c79517b9c74d4095e4388a9
                b3c7ed169f83a079bf4a883cb2bf5d88b9196667f3e2eddc26ea24f192e5ee3e
                f68712a78d1ad086eb776a9be9b0301e50e019fb78c70ada132ab3a5bfd6df92
                e8a78d837f96ac5e7ff5ae5284d4add69b857bbe06312ac0f579d7f1d537ef5d
                b6cb3f775f6a40c85a57ba0f999196368eb0939e68fabfc913d29ff979a1a3e8
                74706d3448d12c2eac1f520d5c833138c67233a6109bc3f13d1c9a763c795cbd
                cf94a0682257a16401deecb7dc787be82ebec9938df10b1d7ffa147661b11a13
                2ed1a432ba87fd76a5be4ac11ebc04e7c151d37b9031e35a9cb7890c2dccaf88
                786f3186acbc3fa033ef3fcdf1a670453b07bb1e4bd96e0bd77749ba9c1ae577
                b5499b24430d7b3d3786a4dd04c7d69fd052f4a192bd58048b4aea01ad7a14a5
                45301c513045cffac021dd9a8640b184b86f915b5967a0996cd6e706e776da68
                00000006
                180968d5c2d73bddf40eb18e436c809891d2689205550e01993e1b9c0c767575
                2dd3d8c2c702aecf34ad3ef16e03b398f1e3d66e490c3d6e086bfbeda5efe599
                4cfa5b469a145858df60fed7c71fe8c1fbdf5fbbe5dbe85c9a51016bd9ccbe9b
                6bea3b494da9d308c8a85db2fa6848c7db1881615a99b452484c67f96be55785
                7b43336be8ff52c5ce5291806e2118337f9a25b26029ec4d1d8023961a5424a5
                ecf2b18beac1653790736e6f6a5c7a95ab77caf54ea06c0c11ad6981a2af0b7c
                a3e964dc09397caf0e78a63d57aeb5c3acb9c65894b134092a2643d992d53107
                5c43c911d5577be8fe88fa023a3e36f32f9333e3c6207ca1b0018c0e0f389827
                a7a4cb92d8b054a206adec09b35ea6615069fc7d49132549bab5548b9e1fe61d
                2b7a9ba0d6d3e0336f17f3caa18e0ea19d6cf0a9c0e48a83cf325369b6a091ba
                """)
        ),

        // Test Case #13
        // LMSigParameters.lms_sha256_m32_h20, LMOtsParameters.sha256_n32_w4
        // LMSigParameters.lms_sha256_m32_h15, LMOtsParameters.sha256_n32_w4
        new TestCase(
            null, // exception
            true, // expected result
            decode("""
                00000002
                00000008
                00000003
                cc453a482bbabfad998dbbacf34c0d89151995177fd38cdfa301b645fbad1675
                ff8083187b30a36242b11bac4bbb7e0c"""),
            decode("""
                466f75722073636f726520616e6420736576656e2079656172732061676f206f
                757220666174686572732062726f7567687420666f727468206f6e2074686973
                20636f6e74696e656e742061206e6577206e6174696f6e2c20636f6e63656976
                656420696e206c6962657274792c20616e642064656469636174656420746f20
                7468652070726f706f736974696f6e207468617420616c6c206d656e20617265
                206372656174656420657175616c2e204e6f772077652061726520656e676167
                656420696e206120677265617420636976696c207761722c2074657374696e67
                20776865746865722074686174206e6174696f6e2c206f7220616e79206e6174
                696f6e20736f20636f6e63656976656420616e6420736f206465646963617465
                642c2063616e206c6f6e6720656e647572652e20576520617265206d6574206f
                6e206120677265617420626174746c656669656c64206f662074686174207761
                722e205765206861766520636f6d6520746f206465646963617465206120706f
                7274696f6e206f662074686174206669656c6420617320612066696e616c2072
                657374696e6720706c61636520666f722074686f73652077686f206865726520
                67617665207468656972206c6976657320746861742074686174206e6174696f
                6e206d69676874206c6976652e20497420697320616c746f6765746865722066
                697474696e6720616e642070726f70657220746861742077652073686f756c64
                20646f20746869732e2042757420696e2061206c61726765722073656e736520
                77652063616e6e6f742064656469636174652c2077652063616e6e6f7420636f
                6e736563726174652c2077652063616e6e6f742068616c6c6f77207468697320
                67726f756e642e20546865206272617665206d656e2c206c6976696e6720616e
                6420646561642c2077686f207374727567676c65642068657265206861766520
                636f6e73656372617465642069742c206661722061626f7665206f757220706f
                6f7220706f77657220746f20616464206f7220646574726163742e2054686520
                776f726c642077696c6c206c6974746c65206e6f74652c206e6f72206c6f6e67
                2072656d656d6265722c20776861742077652073617920686572652c20627574
                2069742063616e206e6576657220666f72676574207768617420746865792064
                696420686572652e20497420697320666f7220757320746865206c6976696e67
                2c207261746865722c20746f2062652064656469636174656420686572652074
                6f2074686520756e66696e697368656420776f726b2077686963682074686579
                2077686f20666f75676874206865726520686176652074687573206661722073
                6f206e6f626c7920616476616e6365642e204974206973207261746865720a0a
                """),
            decode("""
                00000001
                00000000
                00000003
                ae258cca017619f7c85179c0dde1f48122fe5b3adcd5ca14475308c6d6a87c8c
                6cbbbca7a2dbe83a7fa7a0814e3d692b66bec046ed590831e695e0391c0028c4
                9cd5e4e561c983d1640fe534964a4e5725705a4d907f5088b265e329011b8047
                330fcf0030724ce62edb5382e59af394eee06b0fe84d95ff8d22b0ba06c31876
                b85d29135bd4291f49db0f22c1a304ecdea5137b6b59c49cb053c6ec32b276f2
                9dea3ffe6c10f3e99e84b00221bdf587f703e81ffa90e9835839b693fc3e2b06
                1cb47c8e3392750c4f53461e419e151004df01da6d8bf8a7998e88089d18c487
                d1adabe4050214ae3c5aff0b2e7de19a734d6cc06ff060c5ca4ad5c68178fc7c
                bb66b7de65987ce1dc966f1a2f9fbe301f43e6790df0fa452884b3b9ea30fd33
                689cf76ae7eb4f6c79f6fae9e89cd9d0349928757dcdee074eacfeb3e1860d0f
                1e8f335be9d0ad131da1932730fbe5997a813439920d53c4a36c6ebbc4a2b8d6
                96fd511a6b92404421674f1b1b79298243c60bd524cfca71057377b0d0c318fd
                341759a91f4b47f5b0df61d1eb1533982707970789297a1af0bb2fedf8fbd582
                87708cc4c3304246313b323df92f36fa6fa516c333197253c860b2ea4eb92cef
                aa33311f2b9b3af958d67a9e466357f671a1255530fff1c2a7d976c26837bfd5
                d7d9d6ea5f87c81abedf1e1b83602250e2226f54eb8f3f1e00bcfcf241e655e1
                bf79b0d57b947c196f6c33360d303735d323406411416cd1fb2391d3adbbf0e4
                f0ac38767fc2e9ebcec97a5c80712bef5deeaad9c85fc4d024ffe7cba0608c98
                90b023852af96a6dfca090187a4f07447d89b1162d0a65e4cc7e2481b057e199
                0ed2ee333d2c4f26c6321e2b98017fedc0b42202caf469405678a63108359387
                aa240fe210d833914423e02d892fe26290dc2ba89bebdaa2273fe265b5d518dd
                5b406c33656466f865c1ffd671b46e2b9044c256798afb1e1d49dfc025aefc06
                d6cb1a968c5bb3e200944de6a81bce2ee450559fa3e302effeff2b4e919539ff
                3bbeca0575eb8abdc635fd330c1606b1c810029ed55d8a71253ca89587762629
                1aa537e4b0c155e7acbac37d1e447586adc56ad262b0ab2421291a28b4e664c1
                70750274d82b7850fdf745374c2c2eedf9828300b3b2b9f8d2d774063658ad05
                92ed8b8a26e8021ed63f413996d1c12c6a5e80a4fd1f6ede5f431974147d9116
                c356e49fb7cda7d35bde52b1c0efdab1dfea8db8b13c608a5545723ee3611456
                b21da6a343ca0c432a2353dbf926e2f3227e9659618cc6b46f46da614666f33f
                1d979c6d838b678fb027a2ea5ae601592cb28efe10918509418476639bfd1f0f
                7dd8bf91f7b28499dc72039218bfe37595e1741942e4a2f640a92dacd809521d
                df34aa2b549d2b666089a9df02d963347d565cc5fa1af7f700237db8a57f0cee
                4be997ef40f8ae0631877d07b71a4a76d3f6f53a737f14e2fb0f2222e8649144
                c3f8ac33595fd9290a2a0345eb1e4c06187ca4b5ac77098e473a07fe0cb58d88
                1967f7f645ae144a289744b0801165e9c47d5137f79d3538d6f3a45c962d06a0
                13248d125cc52ebd2f3e4ced0d7c1e2429425da6b6aedf96db2c2524522e95a1
                dd83db37e92f782b6db3c84ed3f55f32600003d9602c17b6280fc735787f8381
                853d02d4b03a97a9404bfe6cd4c58c1a67a70298f29094b454aeb1bc0d59e0d5
                e24015f9257d28554905003a687444ce51b19b7e46f4e56e899e3ecdb9415119
                924b0a28f383b7e8f817c6430dd9c7b13b6f93179f77c5678fe51098e4c0425e
                7d44cd2e82c07bff8b8e99ed08d86982ef7680f8ac3ac2228dd286a1acad546d
                43fed64175190ecc62f1662a2f2c29cf2effdc9f9a18e681e96cce1bfb41de22
                5b3c02ba65b4c5cc5591d5b17b64a3e8df530ea10627ed29d096f30934d10954
                a642548ff04651c9f24ba9c0496bdc1a8af76afc27eb3dc9e28247ab5fa7274f
                0cffad5af2cf50ecc35f0b466fd7b8c6973d1ace0cbde0d793cac473ff151aa8
                c9bc3d26beb8d819f21ed0ff1fe6af5be4cb4c61e1ed6897496fbbba14f2719d
                721742fa7249dd251e501b98936ab43d0fcf4b7a2d551471d3e663595e19e235
                674d7d525b6a5ae14ac45913cbfc51e80a7fe351d0cb24a8af1b970d309517df
                7a7dd6b12c6edbaab5addb1711abca1c412eb7270e9a8aeefbf7cc5ca22436dc
                75b0b5989e1c25f578f8d0aadbde0175e67b14ab05a4c5a5e061a030282f5415
                4eebb5a717854b02877ac1fbbb732e52b18dbecaacf16bbb74954f83c0aa470e
                35d099b7efb1e17beeeb87ec0b9e706c331f1b20f0a903dec2b7ca1c196b1d63
                e804513d3f474cbe6f9bbd2900b79b073011004c49d20f7420a01b7745d490c1
                0da63786119d895d58b44eb066d80c88907aaa211dfd82681634be98400099db
                8cb82b6d6172478cc2e63d4ea6dd48b47702b24b6fc7b49e09d87b61d15b59d0
                39a6d49b3e4896ebe0c83c70ee6926d498ace6148ef3449a7830a7ed4923866a
                3f479a708a7dc319c1161d0f29cf425682c389bb173f7681c193ac982c1e4012
                6a122b2c6e3fd20906c2921987b2a20d21c722c5fe899fc15089e7b7ddce4262
                be8acc27fc4b4b5176740f8f3adcf44240fd1d92c5b1db869bf0bc957175fad6
                2d8ad0368b9b47da61e5afffc4ed80ea49890178232dd021d94af7b15bd45059
                67bd91eaa2844169e89320c92914e27eda9800cb81b8ff11a52043439035a275
                53cf146c9c765b3d5f13bd348475a8bf9b3021168364b7cf0b35b4a2df95adda
                7d1d386021d0ddf9193934ae4f0958d2ef8b15a0278e5234aaa976a8ac6acc45
                7fff27c4d64da9d374ddccc55285069074ad79bedfb606c95a5d294e97a49571
                e4b89696b83a69b4c65568e7ba4b4da0903f819a75ee42f6539b4edd45a53dc4
                07c7028834837cb1665050f13b3f69560dec7f042d3d8979fb170257bc024764
                627d369086f127e1f5a95f45cf49817c2c699b8d0b20113259ed141e6c01e23e
                41a7bb7415f324ba3710534126db40da893b8df672bb16a752e873bc3203dd83
                00000008
                24bc09be9c410f127964bbf6430a7b42f53ffe22f04cde135d46a847de0844ac
                c15c1197f938dcb5adfdcceeb52f95ca113148d07192ab76d83185054df0b38d
                8f95cd789b916bf8f7d4b7094802df6ed52cc0573756df7258f12e439cfb1037
                01dd72bbdb60753be01822a1ea210eba7fdcea2c9736e79e11ce2e3b50f21a4e
                0d84d9c578bfcefb4087146a40c95922b2dd69d29103d9f50fe3368b19015172
                36922e837fe8bd50e8063e5f0c2d4a961f1816bf7a5b3eb7ba63761368cae83e
                f2fa4e03491acbbfd6d0a6048e3f2589e67aabeb32b3619085ff2d6a810065b0
                5a2f5526fe1f188ee80bdb9788f68c1e93cc2ea104e404faceef91fa6a28e27c
                e6c2a6a4b03b81c7ca9ce94f06ca7762cb344bc738add901e6496a3fe739343f
                aad8396b832ac93f039adad869401a2e590dda2d9c571e136b55053e8bd17af4
                2e5518ef8b8dea3d9fefb811e7248961af4f67921c5812bb87c27b22d1384109
                85f1f48d40e4c86a1cf7b1d47cb2c776aab2b980a8a1a5e14f6a6c3e823f8b91
                9d7de7705ba30357aad9b1d5e7c3404aee1fca2706bb97e94ee56ce826d631c0
                d03a93e676a557af5b3105c9cce0364907f1c7520a3a9d70ceef18bc86584df8
                c707e48982dcb6221aec3bbe75151d0c50662caf1b401466f089f464b23e4b10
                77f4c2ff6efd0ef42202fd51494093459329eccdf895e038c3f7e2325775c399
                4ee435f799d7cf0ba8308d018a1c748a5b96786d4b1090547165d30cd9e50c9e
                25122424b4d89b5f13949af9eadb7e9bfb95820bfd2053e339dd154593c8912c
                ede4bb64b3dad525f55bf6640d99876654d3a9666ab81b38f1e7d41036a2930a
                3b4ba370e6fb1cb4436eeffb3a1f4029eab69d47e328e3e50034e3facd1ab396
                00000007
                00000003
                ff29b4fe1b544b372b2922adf63187d1a8186efd415d82a993a79ecd884987fb
                984f03f786948f53a3632e75f622a334
                00000000
                00000003
                9818148e2b3f0b1b92424b8df20c31aaaf4d2d49997d481a2be32680e62eac63
                4dfc4d390876d14599c23d59822e0d6f525a9afbc8319f75de7370043d33e413
                7b9f1fbdaeb3e47bdbe29fb7e3247ee5a1c637bd739be597e5513b81e5b717bb
                593037bf97b4c1f216566a944bb19bdac86a5be82fc34eef1799e07d7035692e
                687024660a3a83fe17d73c919513c12660edd8af23975ba3816d027b7cff5e5e
                675f5375bb1eb6eed9e5cc49b962a97b1cf41d79422f2d8e290ff119d1cd562d
                e4f65dff4a7408b2eab21093d096707cbdbb70e8bf6394969e1e51700a500a98
                ca1fa371d96eb24b359db9af3d2a5e125e33b87edc22eca8764eefce39dae0b6
                db5504c256375a7b6bd8ffcaf48a1fa9b246bc9952f8374d8d65cf22439668b0
                3c02ef6fb4a1512dea8d7c38cbd8946114bbd57a0da2ffff326bb83873f4ea40
                09270671b0d9a88e10c9552ac580160d248daa7bc3a10cb393a1a2aca8ed55e6
                d6983861830557e7001d65d8656c07fa37b459a5bc3ff26c2109be9f395d1307
                8e27e75f85f388562f555060fc040650be7c5a4984bfb41fdf0ebc2da22a8719
                563aa522fe36af768d95d49da30c899f3409c8a6587403411eaf032a84354039
                1b136a795a0b3da15351ece2f62b7cc15d8f9e3bbacf29bd73e96c9761fd23ff
                ed75c8131721899b2b314c61537e3d324706de6dbb4adde305943ac6a2000e5f
                6e193f83b9381ade4d61a9ecb2591ece400ace7735eb6e0958d7d1c9b0ef4990
                2ec4592c935303c71049c44a518d65537cd903c95c37811501028a20b4aa4030
                2857c970a589260073ea093eb913a11ac42c67034d27fed38b300eede2ccb18f
                7df10d09d9cbeaccba52471fd01d42361343889e5725a7f2d23c723517337e94
                2f53fcefef8c4f091fc1a02790db7a987bdac6f67382619a84c003fb76f27e1f
                f070bb8b7786ce47d4455de311230a0e1dbccbb8bd37c45f36e84e3f15c1e4e9
                daa1b4a082f85a5c2c71371e39661792284357a7cc40451e39444e8c352185d9
                e7aa1e7524a1eff33b5839c2247b1430a517db6ef4963b23a1579a3803c0d2ae
                3568e0d83d551eb68dcd301e75f4cf38792cc504a94559c1b1a929975aaa38d6
                fee0aefd1aa4c99cb1412736b2f5978c21987e651e391c5b1e61cb84d92d3806
                cb5fe46edde39efbce75d0db86414e82f6ce07b6c304c7f7fa887d13a02e8f59
                57b8f07ae9b80a10b3f050902a230e5b33230473691af8c7c2630d899c8efa1e
                da5afd1db629318736132466c1970f9753200e05a30ee13e38b4a60e15fbac33
                986a6e7760c5805b4f488580db0008e574e173a34cc62fd52c9cd56980249a2f
                0c12238a0df04a06dc9895413b31fd0aa0d9eee50ce7fbc178f8719bd9df399f
                ce72a57e8571fcbfa4f174f0c37c053238bb9225d480547b34eaff09d00fd29a
                d2daf5b694ba4ca8a25e42f14f2cd7c10477ed29b22936989cb7135c8034f81e
                2552fd1d15f54ae69054fa03a36ff48d1ccec9c45dcf642dd4bd0e2aed42d4bc
                b3df5f9a10805347ec0524c13d49014ca1d983d7de40031f530a34ca1e8ba45b
                5b304303a71942388b61779ec6ecefab91cdf705c41d6c2fe72a6be231acc3b5
                496661b0d9f5908d0f39463b785a98348a0f9fcf714255e6d9c9c33549541567
                55980f2d96686e98f5a990f821504cf2a1c32d2b654f0071dfd85981d59da1a0
                ecac2d30b1e2833ea108982aeb059d8c2b7facbcc4391bdd88f900cfae20cfcf
                99fc75fd68a85bee6a75be7cf8f4b8a66d9bcab17094e51e745c9b8980b69cfc
                4d9bbacf338fbaddad1fbcee745ce30b8ea8316e597350bf1372353d38274c31
                34c9e3ddb3affb1f5d5b30a51c3dbbd7529446f4bba26eadf4aa53e7641f70ea
                60710d773b02d930a298f8c6957b71f6841a6391ae9d5d2ebee3600061c57244
                7ff9e16c211a5b71ab1e7ee811f70a76aaf8617648083959b327a8f15698ba87
                3387b492ae7a7ada5d78ae4fdfce67262cacdcaa0717c4e52d7ecd85491903db
                f64073dbc2a0f77ba5489f0af146ce8d4e1544f60cc51d2a9db84eb5d8b3da91
                c10ad2437744c8b3077d592e5b42baa7846d4250df4c98112b688813988a6759
                2fe7b707276dfbcf7cbe3daaf0be3c8c13917d4f2c70c6b6945e05457ce7018e
                c167bebefce80d10502f5a2164492a8602ee7db978c51f79612b5b9d69013105
                05bdcbbb66a385ed2702a630303fcc30900b8d9c345229f7d539185cd2cca328
                d40dbdf3511715cdf195c7565bfcd7ae7830374d4c77acf746874068f7f0ec3f
                bc22fdbd06fbd4a4bc018d6a81bdf9c5f3ec45c441333cee36e2f3d28f4d4ac4
                e45299a3ea7151e2f314ce1d8c7f8aff7be4886bf8ab8de2893f17baf2969125
                d4b8e4f036b3b60c88e0c08450a8e7ed005831f2030760d4a97c419a859ded43
                85e855bed5b966d1a97845fe8a6dc7467a2529ea005fbf0da3fd3efa28142c92
                058dcec7ec1e1f199ac8c777857295b34a33e2b678c04475b7dfeda7656dcc5c
                b948ff2368e989a4688c16ba02479ccb107f6fb27dc30f0e49b9641aff149d07
                c6afd31db92c8a5d0c0f4234aec0c0e1e05c7336378b387d1a70a4176dee6835
                74811cce4b20f0730ef92932d1d790b6cf73081da8c51b75ba8950579b92c117
                567f3a1fd8a049685a7aacd9cbb997a0aff7e6a34ea7e70fc8cf24b11f96d2f9
                ef5327eba013bea5cff327f3a5aece1b8a2fb45f80c2454a9cba86a55a1ef63c
                7862d07eb1274fb68b69db5a0cab9b5aba53595f6e0cf643efbf38ed33e7bdb7
                3f887e8bc0e50c003bb8523b7aa459bd0517fd3b502ad4fdaa22010cb6ac5bf9
                7d6ad67c2317a9d18fa8efc4c02345bba30bdfeb788c001ff4f9a899b0d11043
                454b52fc2828fc1891e149b42ef897608d95f568f3eb301023dbf18f40da3148
                45821078487199e71a6b48b2105851702fb6319052ca642ed6338c41cc8b3d95
                b101a08835e9352f71938d24f8789d32add82e7ede0fd1858330a451015f7e87
                9c5eb59c8e534beb771aee8b0fb2bf4937fdc9cf07d891ccda61ae4aad303282
                00000007
                2e0e7708bb0e589d2d818a8c0e2c53b3e59b9e43c7a194fd18d19ab1554a9f85
                90b31f08e2fe1b38486572cc3b36ddaa9d85b795fcd93acd531283688191b5f5
                a744b89faae49989127685cdc000e001a0d77df3a5c3061e312377b1050e7371
                24a68d2a00a848e141d274dccb8e4740d33ef7970494ed316447f8381ba06791
                001e90b7f36ef24e1dbbd68f7074ddd233d9e15cbd4efa4a249cb30fd3095c3d
                ed096e87d6c179ff8dbadb1bc6493bb6f944ccee2cbf24573017817e586475f0
                ed51bfe889b298a2fb76d16dde0c966a70a284dafa980442f870d640e11079d0
                a4f6834a62ba0a4eac4d7334f3c756ea6b0bd8eafad227b5b8eb4e937c32412f
                201780dbf5eab317f3a21293e653115bbffac4899830eb28e6e43c1a77b51884
                8f68887ccfa366175be2a88d3fc178e671073736bd94eb4e16720a6b3ee119b6
                dcba885ecb46126614c7a677c1662c4cadcda742f27fc01a8bd5af474ee4a29b
                4e25721bb931b8bf898afb3cb66d3fcab70b80005e737ec5bd88d5ced8941226
                720dd43655a9ba1d4bf0a723faa4bb3651ed2ea7e0bd08113e524777e6ec592a
                ba5cab16b084d208d20bf25ad9a7ae31bceb00b07ef20cab7d1f6883ac331c75
                a2aefb8230ae97dc34577785b123af406040d01fd072c493228d7583cd023c25
                """)
        ),

        // Test Case #14
        // LMS signature length is incorrect
        new TestCase(
            new SignatureException(),
            false, // expected result
            decode("""
                00000002
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b850650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878"""),
                decode("""
                54686520706f77657273206e6f742064656c65676174656420746f2074686520
                556e69746564205374617465732062792074686520436f6e737469747574696f
                6e2c206e6f722070726f6869626974656420627920697420746f207468652053
                74617465732c2061726520726573657276656420746f20746865205374617465
                7320726573706563746976656c792c206f7220746f207468652070656f706c65
                2e0a"""),
            decode("""
                00000001
                00000005
                00000004
                d32b56671d7eb98833c49b433c272586bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb69128ec45146f86292f9dff9610a7bf95
                a64c7f60f6261a62043f86c70324b7707f5b4a8a6e19c114c7be866d488778a0
                e05fd5c6509a6e61d559cf1a77a970de927d60c70d3de31a7fa0100994e162a2
                582e8ff1b10cd99d4e8e413ef469559f7d7ed12c838342f9b9c96b83a4943d16
                81d84b15357ff48ca579f19f5e71f18466f2bbef4bf660c2518eb20de2f66e3b
                14784269d7d876f5d35d3fbfc7039a462c716bb9f6891a7f41ad133e9e1f6d95
                60b960e7777c52f060492f2d7c660e1471e07e72655562035abc9a701b473ecb
                c3943c6b9c4f2405a3cb8bf8a691ca51d3f6ad2f428bab6f3a30f55dd9625563
                f0a75ee390e385e3ae0b906961ecf41ae073a0590c2eb6204f44831c26dd768c
                35b167b28ce8dc988a3748255230cef99ebf14e730632f27414489808afab1d1
                e783ed04516de012498682212b07810579b250365941bcc98142da13609e9768
                aaf65de7620dabec29eb82a17fde35af15ad238c73f81bdb8dec2fc0e7f93270
                1099762b37f43c4a3c20010a3d72e2f606be108d310e639f09ce7286800d9ef8
                a1a40281cc5a7ea98d2adc7c7400c2fe5a101552df4e3cccfd0cbf2ddf5dc677
                9cbbc68fee0c3efe4ec22b83a2caa3e48e0809a0a750b73ccdcf3c79e6580c15
                4f8a58f7f24335eec5c5eb5e0cf01dcf4439424095fceb077f66ded5bec73b27
                c5b9f64a2a9af2f07c05e99e5cf80f00252e39db32f6c19674f190c9fbc506d8
                26857713afd2ca6bb85cd8c107347552f30575a5417816ab4db3f603f2df56fb
                c413e7d0acd8bdd81352b2471fc1bc4f1ef296fea1220403466b1afe78b94f7e
                cf7cc62fb92be14f18c2192384ebceaf8801afdf947f698ce9c6ceb696ed70e9
                e87b0144417e8d7baf25eb5f70f09f016fc925b4db048ab8d8cb2a661ce3b57a
                da67571f5dd546fc22cb1f97e0ebd1a65926b1234fd04f171cf469c76b884cf3
                115cce6f792cc84e36da58960c5f1d760f32c12faef477e94c92eb75625b6a37
                1efc72d60ca5e908b3a7dd69fef0249150e3eebdfed39cbdc3ce9704882a2072
                c75e13527b7a581a556168783dc1e97545e31865ddc46b3c957835da252bb732
                8d3ee2062445dfb85ef8c35f8e1f3371af34023cef626e0af1e0bc017351aae2
                ab8f5c612ead0b729a1d059d02bfe18efa971b7300e882360a93b025ff97e9e0
                eec0f3f3f13039a17f88b0cf808f488431606cb13f9241f40f44e537d302c64a
                4f1f4ab949b9feefadcb71ab50ef27d6d6ca8510f150c85fb525bf25703df720
                9b6066f09c37280d59128d2f0f637c7d7d7fad4ed1c1ea04e628d221e3d8db77
                b7c878c9411cafc5071a34a00f4cf07738912753dfce48f07576f0d4f94f42c6
                d76f7ce973e9367095ba7e9a3649b7f461d9f9ac1332a4d1044c96aefee67676
                401b64457c54d65fef6500c59cdfb69af7b6dddfcb0f086278dd8ad0686078df
                b0f3f79cd893d314168648499898fbc0ced5f95b74e8ff14d735cdea968bee74
                00000005
                d8b8112f9200a5e50c4a262165bd342cd800b8496810bc716277435ac376728d
                129ac6eda839a6f357b5a04387c5ce97382a78f2a4372917eefcbf93f63bb591
                12f5dbe400bd49e4501e859f885bf0736e90a509b30a26bfac8c17b5991c157e
                b5971115aa39efd8d564a6b90282c3168af2d30ef89d51bf14654510a12b8a14
                4cca1848cf7da59cc2b3d9d0692dd2a20ba3863480e25b1b85ee860c62bf5136
                00000005
                00000004
                d2f14ff6346af964569f7d6cb880a1b66c5004917da6eafe4d9ef6c6407b3db0
                e5485b122d9ebe15cda93cfec582d7ab
                0000000a
                00000004
                0703c491e7558b35011ece3592eaa5da4d918786771233e8353bc4f62323185c
                95cae05b899e35dffd717054706209988ebfdf6e37960bb5c38d7657e8bffeef
                9bc042da4b4525650485c66d0ce19b317587c6ba4bffcc428e25d08931e72dfb
                6a120c5612344258b85efdb7db1db9e1865a73caf96557eb39ed3e3f426933ac
                9eeddb03a1d2374af7bf77185577456237f9de2d60113c23f846df26fa942008
                a698994c0827d90e86d43e0df7f4bfcdb09b86a373b98288b7094ad81a0185ac
                100e4f2c5fc38c003c1ab6fea479eb2f5ebe48f584d7159b8ada03586e65ad9c
                969f6aecbfe44cf356888a7b15a3ff074f771760b26f9c04884ee1faa329fbf4
                e61af23aee7fa5d4d9a5dfcf43c4c26ce8aea2ce8a2990d7ba7b57108b47dabf
                beadb2b25b3cacc1ac0cef346cbb90fb044beee4fac2603a442bdf7e507243b7
                319c9944b1586e899d431c7f91bcccc8690dbf59b28386b2315f3d36ef2eaa3c
                f30b2b51f48b71b003dfb08249484201043f65f5a3ef6bbd61ddfee81aca9ce6
                0081262a00000480dcbc9a3da6fbef5c1c0a55e48a0e729f9184fcb1407c3152
                9db268f6fe50032a363c9801306837fafabdf957fd97eafc80dbd165e435d0e2
                dfd836a28b354023924b6fb7e48bc0b3ed95eea64c2d402f4d734c8dc26f3ac5
                91825daef01eae3c38e3328d00a77dc657034f287ccb0f0e1c9a7cbdc828f627
                205e4737b84b58376551d44c12c3c215c812a0970789c83de51d6ad787271963
                327f0a5fbb6b5907dec02c9a90934af5a1c63b72c82653605d1dcce51596b3c2
                b45696689f2eb382007497557692caac4d57b5de9f5569bc2ad0137fd47fb47e
                664fcb6db4971f5b3e07aceda9ac130e9f38182de994cff192ec0e82fd6d4cb7
                f3fe00812589b7a7ce515440456433016b84a59bec6619a1c6c0b37dd1450ed4
                f2d8b584410ceda8025f5d2d8dd0d2176fc1cf2cc06fa8c82bed4d944e71339e
                ce780fd025bd41ec34ebff9d4270a3224e019fcb444474d482fd2dbe75efb203
                89cc10cd600abb54c47ede93e08c114edb04117d714dc1d525e11bed8756192f
                929d15462b939ff3f52f2252da2ed64d8fae88818b1efa2c7b08c8794fb1b214
                aa233db3162833141ea4383f1a6f120be1db82ce3630b3429114463157a64e91
                234d475e2f79cbf05e4db6a9407d72c6bff7d1198b5c4d6aad2831db61274993
                715a0182c7dc8089e32c8531deed4f7431c07c02195eba2ef91efb5613c37af7
                ae0c066babc69369700e1dd26eddc0d216c781d56e4ce47e3303fa73007ff7b9
                49ef23be2aa4dbf25206fe45c20dd888395b2526391a724996a44156beac8082
                12858792bf8e74cba49dee5e8812e019da87454bff9e847ed83db07af3137430
                82f880a278f682c2bd0ad6887cb59f652e155987d61bbf6a88d36ee93b6072e6
                656d9ccbaae3d655852e38deb3a2dcf8058dc9fb6f2ab3d3b3539eb77b248a66
                1091d05eb6e2f297774fe6053598457cc61908318de4b826f0fc86d4bb117d33
                e865aa805009cc2918d9c2f840c4da43a703ad9f5b5806163d7161696b5a0adc
                00000006 // changed from 5 to 6
                d5c0d1bebb06048ed6fe2ef2c6cef305b3ed633941ebc8b3bec9738754cddd60
                e1920ada52f43d055b5031cee6192520d6a5115514851ce7fd448d4a39fae2ab
                2335b525f484e9b40d6a4a969394843bdcf6d14c48e8015e08ab92662c05c6e9
                f90b65a7a6201689999f32bfd368e5e3ec9cb70ac7b8399003f175c40885081a
                09ab3034911fe125631051df0408b3946b0bde790911e8978ba07dd56c73e7ee
                """)
        ),

        // Test Case #15
        // HSS signature and public key have different tree heights
        new TestCase(
            new SignatureException(),
            false, // expected result
            decode("""
                00000003 // level 2 changed to level 3
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b850650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878"""),
            decode("""
                54686520706f77657273206e6f742064656c65676174656420746f2074686520
                556e69746564205374617465732062792074686520436f6e737469747574696f
                6e2c206e6f722070726f6869626974656420627920697420746f207468652053
                74617465732c2061726520726573657276656420746f20746865205374617465
                7320726573706563746976656c792c206f7220746f207468652070656f706c65
                2e0a"""),
            decode("""
                00000001
                00000005
                00000004
                d32b56671d7eb98833c49b433c272586bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb69128ec45146f86292f9dff9610a7bf95
                a64c7f60f6261a62043f86c70324b7707f5b4a8a6e19c114c7be866d488778a0
                e05fd5c6509a6e61d559cf1a77a970de927d60c70d3de31a7fa0100994e162a2
                582e8ff1b10cd99d4e8e413ef469559f7d7ed12c838342f9b9c96b83a4943d16
                81d84b15357ff48ca579f19f5e71f18466f2bbef4bf660c2518eb20de2f66e3b
                14784269d7d876f5d35d3fbfc7039a462c716bb9f6891a7f41ad133e9e1f6d95
                60b960e7777c52f060492f2d7c660e1471e07e72655562035abc9a701b473ecb
                c3943c6b9c4f2405a3cb8bf8a691ca51d3f6ad2f428bab6f3a30f55dd9625563
                f0a75ee390e385e3ae0b906961ecf41ae073a0590c2eb6204f44831c26dd768c
                35b167b28ce8dc988a3748255230cef99ebf14e730632f27414489808afab1d1
                e783ed04516de012498682212b07810579b250365941bcc98142da13609e9768
                aaf65de7620dabec29eb82a17fde35af15ad238c73f81bdb8dec2fc0e7f93270
                1099762b37f43c4a3c20010a3d72e2f606be108d310e639f09ce7286800d9ef8
                a1a40281cc5a7ea98d2adc7c7400c2fe5a101552df4e3cccfd0cbf2ddf5dc677
                9cbbc68fee0c3efe4ec22b83a2caa3e48e0809a0a750b73ccdcf3c79e6580c15
                4f8a58f7f24335eec5c5eb5e0cf01dcf4439424095fceb077f66ded5bec73b27
                c5b9f64a2a9af2f07c05e99e5cf80f00252e39db32f6c19674f190c9fbc506d8
                26857713afd2ca6bb85cd8c107347552f30575a5417816ab4db3f603f2df56fb
                c413e7d0acd8bdd81352b2471fc1bc4f1ef296fea1220403466b1afe78b94f7e
                cf7cc62fb92be14f18c2192384ebceaf8801afdf947f698ce9c6ceb696ed70e9
                e87b0144417e8d7baf25eb5f70f09f016fc925b4db048ab8d8cb2a661ce3b57a
                da67571f5dd546fc22cb1f97e0ebd1a65926b1234fd04f171cf469c76b884cf3
                115cce6f792cc84e36da58960c5f1d760f32c12faef477e94c92eb75625b6a37
                1efc72d60ca5e908b3a7dd69fef0249150e3eebdfed39cbdc3ce9704882a2072
                c75e13527b7a581a556168783dc1e97545e31865ddc46b3c957835da252bb732
                8d3ee2062445dfb85ef8c35f8e1f3371af34023cef626e0af1e0bc017351aae2
                ab8f5c612ead0b729a1d059d02bfe18efa971b7300e882360a93b025ff97e9e0
                eec0f3f3f13039a17f88b0cf808f488431606cb13f9241f40f44e537d302c64a
                4f1f4ab949b9feefadcb71ab50ef27d6d6ca8510f150c85fb525bf25703df720
                9b6066f09c37280d59128d2f0f637c7d7d7fad4ed1c1ea04e628d221e3d8db77
                b7c878c9411cafc5071a34a00f4cf07738912753dfce48f07576f0d4f94f42c6
                d76f7ce973e9367095ba7e9a3649b7f461d9f9ac1332a4d1044c96aefee67676
                401b64457c54d65fef6500c59cdfb69af7b6dddfcb0f086278dd8ad0686078df
                b0f3f79cd893d314168648499898fbc0ced5f95b74e8ff14d735cdea968bee74
                00000005
                d8b8112f9200a5e50c4a262165bd342cd800b8496810bc716277435ac376728d
                129ac6eda839a6f357b5a04387c5ce97382a78f2a4372917eefcbf93f63bb591
                12f5dbe400bd49e4501e859f885bf0736e90a509b30a26bfac8c17b5991c157e
                b5971115aa39efd8d564a6b90282c3168af2d30ef89d51bf14654510a12b8a14
                4cca1848cf7da59cc2b3d9d0692dd2a20ba3863480e25b1b85ee860c62bf5136
                00000005
                00000004
                d2f14ff6346af964569f7d6cb880a1b66c5004917da6eafe4d9ef6c6407b3db0
                e5485b122d9ebe15cda93cfec582d7ab
                0000000a
                00000004
                0703c491e7558b35011ece3592eaa5da4d918786771233e8353bc4f62323185c
                95cae05b899e35dffd717054706209988ebfdf6e37960bb5c38d7657e8bffeef
                9bc042da4b4525650485c66d0ce19b317587c6ba4bffcc428e25d08931e72dfb
                6a120c5612344258b85efdb7db1db9e1865a73caf96557eb39ed3e3f426933ac
                9eeddb03a1d2374af7bf77185577456237f9de2d60113c23f846df26fa942008
                a698994c0827d90e86d43e0df7f4bfcdb09b86a373b98288b7094ad81a0185ac
                100e4f2c5fc38c003c1ab6fea479eb2f5ebe48f584d7159b8ada03586e65ad9c
                969f6aecbfe44cf356888a7b15a3ff074f771760b26f9c04884ee1faa329fbf4
                e61af23aee7fa5d4d9a5dfcf43c4c26ce8aea2ce8a2990d7ba7b57108b47dabf
                beadb2b25b3cacc1ac0cef346cbb90fb044beee4fac2603a442bdf7e507243b7
                319c9944b1586e899d431c7f91bcccc8690dbf59b28386b2315f3d36ef2eaa3c
                f30b2b51f48b71b003dfb08249484201043f65f5a3ef6bbd61ddfee81aca9ce6
                0081262a00000480dcbc9a3da6fbef5c1c0a55e48a0e729f9184fcb1407c3152
                9db268f6fe50032a363c9801306837fafabdf957fd97eafc80dbd165e435d0e2
                dfd836a28b354023924b6fb7e48bc0b3ed95eea64c2d402f4d734c8dc26f3ac5
                91825daef01eae3c38e3328d00a77dc657034f287ccb0f0e1c9a7cbdc828f627
                205e4737b84b58376551d44c12c3c215c812a0970789c83de51d6ad787271963
                327f0a5fbb6b5907dec02c9a90934af5a1c63b72c82653605d1dcce51596b3c2
                b45696689f2eb382007497557692caac4d57b5de9f5569bc2ad0137fd47fb47e
                664fcb6db4971f5b3e07aceda9ac130e9f38182de994cff192ec0e82fd6d4cb7
                f3fe00812589b7a7ce515440456433016b84a59bec6619a1c6c0b37dd1450ed4
                f2d8b584410ceda8025f5d2d8dd0d2176fc1cf2cc06fa8c82bed4d944e71339e
                ce780fd025bd41ec34ebff9d4270a3224e019fcb444474d482fd2dbe75efb203
                89cc10cd600abb54c47ede93e08c114edb04117d714dc1d525e11bed8756192f
                929d15462b939ff3f52f2252da2ed64d8fae88818b1efa2c7b08c8794fb1b214
                aa233db3162833141ea4383f1a6f120be1db82ce3630b3429114463157a64e91
                234d475e2f79cbf05e4db6a9407d72c6bff7d1198b5c4d6aad2831db61274993
                715a0182c7dc8089e32c8531deed4f7431c07c02195eba2ef91efb5613c37af7
                ae0c066babc69369700e1dd26eddc0d216c781d56e4ce47e3303fa73007ff7b9
                49ef23be2aa4dbf25206fe45c20dd888395b2526391a724996a44156beac8082
                12858792bf8e74cba49dee5e8812e019da87454bff9e847ed83db07af3137430
                82f880a278f682c2bd0ad6887cb59f652e155987d61bbf6a88d36ee93b6072e6
                656d9ccbaae3d655852e38deb3a2dcf8058dc9fb6f2ab3d3b3539eb77b248a66
                1091d05eb6e2f297774fe6053598457cc61908318de4b826f0fc86d4bb117d33
                e865aa805009cc2918d9c2f840c4da43a703ad9f5b5806163d7161696b5a0adc
                00000005
                d5c0d1bebb06048ed6fe2ef2c6cef305b3ed633941ebc8b3bec9738754cddd60
                e1920ada52f43d055b5031cee6192520d6a5115514851ce7fd448d4a39fae2ab
                2335b525f484e9b40d6a4a969394843bdcf6d14c48e8015e08ab92662c05c6e9
                f90b65a7a6201689999f32bfd368e5e3ec9cb70ac7b8399003f175c40885081a
                09ab3034911fe125631051df0408b3946b0bde790911e8978ba07dd56c73e7ee
                """)
        ),

        // Test Case #16
        // bad signature
        new TestCase(
            null,  // exception
            false, // expected result
            decode("""
                00000002
                00000006 // changed 5 to 6
                00000004
                61a5d57d37f5e46bfb7520806b07a1b850650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878"""),
            decode("""
                54686520706f77657273206e6f742064656c65676174656420746f2074686520
                556e69746564205374617465732062792074686520436f6e737469747574696f
                6e2c206e6f722070726f6869626974656420627920697420746f207468652053
                74617465732c2061726520726573657276656420746f20746865205374617465
                7320726573706563746976656c792c206f7220746f207468652070656f706c65
                2e0a"""),
            decode("""
                00000001
                00000005
                00000004
                d32b56671d7eb98833c49b433c272586bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb69128ec45146f86292f9dff9610a7bf95
                a64c7f60f6261a62043f86c70324b7707f5b4a8a6e19c114c7be866d488778a0
                e05fd5c6509a6e61d559cf1a77a970de927d60c70d3de31a7fa0100994e162a2
                582e8ff1b10cd99d4e8e413ef469559f7d7ed12c838342f9b9c96b83a4943d16
                81d84b15357ff48ca579f19f5e71f18466f2bbef4bf660c2518eb20de2f66e3b
                14784269d7d876f5d35d3fbfc7039a462c716bb9f6891a7f41ad133e9e1f6d95
                60b960e7777c52f060492f2d7c660e1471e07e72655562035abc9a701b473ecb
                c3943c6b9c4f2405a3cb8bf8a691ca51d3f6ad2f428bab6f3a30f55dd9625563
                f0a75ee390e385e3ae0b906961ecf41ae073a0590c2eb6204f44831c26dd768c
                35b167b28ce8dc988a3748255230cef99ebf14e730632f27414489808afab1d1
                e783ed04516de012498682212b07810579b250365941bcc98142da13609e9768
                aaf65de7620dabec29eb82a17fde35af15ad238c73f81bdb8dec2fc0e7f93270
                1099762b37f43c4a3c20010a3d72e2f606be108d310e639f09ce7286800d9ef8
                a1a40281cc5a7ea98d2adc7c7400c2fe5a101552df4e3cccfd0cbf2ddf5dc677
                9cbbc68fee0c3efe4ec22b83a2caa3e48e0809a0a750b73ccdcf3c79e6580c15
                4f8a58f7f24335eec5c5eb5e0cf01dcf4439424095fceb077f66ded5bec73b27
                c5b9f64a2a9af2f07c05e99e5cf80f00252e39db32f6c19674f190c9fbc506d8
                26857713afd2ca6bb85cd8c107347552f30575a5417816ab4db3f603f2df56fb
                c413e7d0acd8bdd81352b2471fc1bc4f1ef296fea1220403466b1afe78b94f7e
                cf7cc62fb92be14f18c2192384ebceaf8801afdf947f698ce9c6ceb696ed70e9
                e87b0144417e8d7baf25eb5f70f09f016fc925b4db048ab8d8cb2a661ce3b57a
                da67571f5dd546fc22cb1f97e0ebd1a65926b1234fd04f171cf469c76b884cf3
                115cce6f792cc84e36da58960c5f1d760f32c12faef477e94c92eb75625b6a37
                1efc72d60ca5e908b3a7dd69fef0249150e3eebdfed39cbdc3ce9704882a2072
                c75e13527b7a581a556168783dc1e97545e31865ddc46b3c957835da252bb732
                8d3ee2062445dfb85ef8c35f8e1f3371af34023cef626e0af1e0bc017351aae2
                ab8f5c612ead0b729a1d059d02bfe18efa971b7300e882360a93b025ff97e9e0
                eec0f3f3f13039a17f88b0cf808f488431606cb13f9241f40f44e537d302c64a
                4f1f4ab949b9feefadcb71ab50ef27d6d6ca8510f150c85fb525bf25703df720
                9b6066f09c37280d59128d2f0f637c7d7d7fad4ed1c1ea04e628d221e3d8db77
                b7c878c9411cafc5071a34a00f4cf07738912753dfce48f07576f0d4f94f42c6
                d76f7ce973e9367095ba7e9a3649b7f461d9f9ac1332a4d1044c96aefee67676
                401b64457c54d65fef6500c59cdfb69af7b6dddfcb0f086278dd8ad0686078df
                b0f3f79cd893d314168648499898fbc0ced5f95b74e8ff14d735cdea968bee74
                00000005
                d8b8112f9200a5e50c4a262165bd342cd800b8496810bc716277435ac376728d
                129ac6eda839a6f357b5a04387c5ce97382a78f2a4372917eefcbf93f63bb591
                12f5dbe400bd49e4501e859f885bf0736e90a509b30a26bfac8c17b5991c157e
                b5971115aa39efd8d564a6b90282c3168af2d30ef89d51bf14654510a12b8a14
                4cca1848cf7da59cc2b3d9d0692dd2a20ba3863480e25b1b85ee860c62bf5136
                00000005
                00000004
                d2f14ff6346af964569f7d6cb880a1b66c5004917da6eafe4d9ef6c6407b3db0
                e5485b122d9ebe15cda93cfec582d7ab
                0000000a
                00000004
                0703c491e7558b35011ece3592eaa5da4d918786771233e8353bc4f62323185c
                95cae05b899e35dffd717054706209988ebfdf6e37960bb5c38d7657e8bffeef
                9bc042da4b4525650485c66d0ce19b317587c6ba4bffcc428e25d08931e72dfb
                6a120c5612344258b85efdb7db1db9e1865a73caf96557eb39ed3e3f426933ac
                9eeddb03a1d2374af7bf77185577456237f9de2d60113c23f846df26fa942008
                a698994c0827d90e86d43e0df7f4bfcdb09b86a373b98288b7094ad81a0185ac
                100e4f2c5fc38c003c1ab6fea479eb2f5ebe48f584d7159b8ada03586e65ad9c
                969f6aecbfe44cf356888a7b15a3ff074f771760b26f9c04884ee1faa329fbf4
                e61af23aee7fa5d4d9a5dfcf43c4c26ce8aea2ce8a2990d7ba7b57108b47dabf
                beadb2b25b3cacc1ac0cef346cbb90fb044beee4fac2603a442bdf7e507243b7
                319c9944b1586e899d431c7f91bcccc8690dbf59b28386b2315f3d36ef2eaa3c
                f30b2b51f48b71b003dfb08249484201043f65f5a3ef6bbd61ddfee81aca9ce6
                0081262a00000480dcbc9a3da6fbef5c1c0a55e48a0e729f9184fcb1407c3152
                9db268f6fe50032a363c9801306837fafabdf957fd97eafc80dbd165e435d0e2
                dfd836a28b354023924b6fb7e48bc0b3ed95eea64c2d402f4d734c8dc26f3ac5
                91825daef01eae3c38e3328d00a77dc657034f287ccb0f0e1c9a7cbdc828f627
                205e4737b84b58376551d44c12c3c215c812a0970789c83de51d6ad787271963
                327f0a5fbb6b5907dec02c9a90934af5a1c63b72c82653605d1dcce51596b3c2
                b45696689f2eb382007497557692caac4d57b5de9f5569bc2ad0137fd47fb47e
                664fcb6db4971f5b3e07aceda9ac130e9f38182de994cff192ec0e82fd6d4cb7
                f3fe00812589b7a7ce515440456433016b84a59bec6619a1c6c0b37dd1450ed4
                f2d8b584410ceda8025f5d2d8dd0d2176fc1cf2cc06fa8c82bed4d944e71339e
                ce780fd025bd41ec34ebff9d4270a3224e019fcb444474d482fd2dbe75efb203
                89cc10cd600abb54c47ede93e08c114edb04117d714dc1d525e11bed8756192f
                929d15462b939ff3f52f2252da2ed64d8fae88818b1efa2c7b08c8794fb1b214
                aa233db3162833141ea4383f1a6f120be1db82ce3630b3429114463157a64e91
                234d475e2f79cbf05e4db6a9407d72c6bff7d1198b5c4d6aad2831db61274993
                715a0182c7dc8089e32c8531deed4f7431c07c02195eba2ef91efb5613c37af7
                ae0c066babc69369700e1dd26eddc0d216c781d56e4ce47e3303fa73007ff7b9
                49ef23be2aa4dbf25206fe45c20dd888395b2526391a724996a44156beac8082
                12858792bf8e74cba49dee5e8812e019da87454bff9e847ed83db07af3137430
                82f880a278f682c2bd0ad6887cb59f652e155987d61bbf6a88d36ee93b6072e6
                656d9ccbaae3d655852e38deb3a2dcf8058dc9fb6f2ab3d3b3539eb77b248a66
                1091d05eb6e2f297774fe6053598457cc61908318de4b826f0fc86d4bb117d33
                e865aa805009cc2918d9c2f840c4da43a703ad9f5b5806163d7161696b5a0adc
                00000005
                d5c0d1bebb06048ed6fe2ef2c6cef305b3ed633941ebc8b3bec9738754cddd60
                e1920ada52f43d055b5031cee6192520d6a5115514851ce7fd448d4a39fae2ab
                2335b525f484e9b40d6a4a969394843bdcf6d14c48e8015e08ab92662c05c6e9
                f90b65a7a6201689999f32bfd368e5e3ec9cb70ac7b8399003f175c40885081a
                09ab3034911fe125631051df0408b3946b0bde790911e8978ba07dd56c73e7ee
                """)
        ),

        // Test Case #17
        // Invalid key in HSS signature
        new TestCase(
            new SignatureException(),
            false, // expected result
            decode("""
                00000002
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b850650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878"""),
            decode("""
                54686520706f77657273206e6f742064656c65676174656420746f2074686520
                556e69746564205374617465732062792074686520436f6e737469747574696f
                6e2c206e6f722070726f6869626974656420627920697420746f207468652053
                74617465732c2061726520726573657276656420746f20746865205374617465
                7320726573706563746976656c792c206f7220746f207468652070656f706c65
                2e0a"""),
            decode("""
                00000001
                00000005
                00000004
                d32b56671d7eb98833c49b433c272586bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb69128ec45146f86292f9dff9610a7bf95
                a64c7f60f6261a62043f86c70324b7707f5b4a8a6e19c114c7be866d488778a0
                e05fd5c6509a6e61d559cf1a77a970de927d60c70d3de31a7fa0100994e162a2
                582e8ff1b10cd99d4e8e413ef469559f7d7ed12c838342f9b9c96b83a4943d16
                81d84b15357ff48ca579f19f5e71f18466f2bbef4bf660c2518eb20de2f66e3b
                14784269d7d876f5d35d3fbfc7039a462c716bb9f6891a7f41ad133e9e1f6d95
                60b960e7777c52f060492f2d7c660e1471e07e72655562035abc9a701b473ecb
                c3943c6b9c4f2405a3cb8bf8a691ca51d3f6ad2f428bab6f3a30f55dd9625563
                f0a75ee390e385e3ae0b906961ecf41ae073a0590c2eb6204f44831c26dd768c
                35b167b28ce8dc988a3748255230cef99ebf14e730632f27414489808afab1d1
                e783ed04516de012498682212b07810579b250365941bcc98142da13609e9768
                aaf65de7620dabec29eb82a17fde35af15ad238c73f81bdb8dec2fc0e7f93270
                1099762b37f43c4a3c20010a3d72e2f606be108d310e639f09ce7286800d9ef8
                a1a40281cc5a7ea98d2adc7c7400c2fe5a101552df4e3cccfd0cbf2ddf5dc677
                9cbbc68fee0c3efe4ec22b83a2caa3e48e0809a0a750b73ccdcf3c79e6580c15
                4f8a58f7f24335eec5c5eb5e0cf01dcf4439424095fceb077f66ded5bec73b27
                c5b9f64a2a9af2f07c05e99e5cf80f00252e39db32f6c19674f190c9fbc506d8
                26857713afd2ca6bb85cd8c107347552f30575a5417816ab4db3f603f2df56fb
                c413e7d0acd8bdd81352b2471fc1bc4f1ef296fea1220403466b1afe78b94f7e
                cf7cc62fb92be14f18c2192384ebceaf8801afdf947f698ce9c6ceb696ed70e9
                e87b0144417e8d7baf25eb5f70f09f016fc925b4db048ab8d8cb2a661ce3b57a
                da67571f5dd546fc22cb1f97e0ebd1a65926b1234fd04f171cf469c76b884cf3
                115cce6f792cc84e36da58960c5f1d760f32c12faef477e94c92eb75625b6a37
                1efc72d60ca5e908b3a7dd69fef0249150e3eebdfed39cbdc3ce9704882a2072
                c75e13527b7a581a556168783dc1e97545e31865ddc46b3c957835da252bb732
                8d3ee2062445dfb85ef8c35f8e1f3371af34023cef626e0af1e0bc017351aae2
                ab8f5c612ead0b729a1d059d02bfe18efa971b7300e882360a93b025ff97e9e0
                eec0f3f3f13039a17f88b0cf808f488431606cb13f9241f40f44e537d302c64a
                4f1f4ab949b9feefadcb71ab50ef27d6d6ca8510f150c85fb525bf25703df720
                9b6066f09c37280d59128d2f0f637c7d7d7fad4ed1c1ea04e628d221e3d8db77
                b7c878c9411cafc5071a34a00f4cf07738912753dfce48f07576f0d4f94f42c6
                d76f7ce973e9367095ba7e9a3649b7f461d9f9ac1332a4d1044c96aefee67676
                401b64457c54d65fef6500c59cdfb69af7b6dddfcb0f086278dd8ad0686078df
                b0f3f79cd893d314168648499898fbc0ced5f95b74e8ff14d735cdea968bee74
                00000006 // changed 5 to 6
                d8b8112f9200a5e50c4a262165bd342cd800b8496810bc716277435ac376728d
                129ac6eda839a6f357b5a04387c5ce97382a78f2a4372917eefcbf93f63bb591
                12f5dbe400bd49e4501e859f885bf0736e90a509b30a26bfac8c17b5991c157e
                b5971115aa39efd8d564a6b90282c3168af2d30ef89d51bf14654510a12b8a14
                4cca1848cf7da59cc2b3d9d0692dd2a20ba3863480e25b1b85ee860c62bf5136
                00000005
                00000004
                d2f14ff6346af964569f7d6cb880a1b66c5004917da6eafe4d9ef6c6407b3db0
                e5485b122d9ebe15cda93cfec582d7ab
                0000000a
                00000004
                0703c491e7558b35011ece3592eaa5da4d918786771233e8353bc4f62323185c
                95cae05b899e35dffd717054706209988ebfdf6e37960bb5c38d7657e8bffeef
                9bc042da4b4525650485c66d0ce19b317587c6ba4bffcc428e25d08931e72dfb
                6a120c5612344258b85efdb7db1db9e1865a73caf96557eb39ed3e3f426933ac
                9eeddb03a1d2374af7bf77185577456237f9de2d60113c23f846df26fa942008
                a698994c0827d90e86d43e0df7f4bfcdb09b86a373b98288b7094ad81a0185ac
                100e4f2c5fc38c003c1ab6fea479eb2f5ebe48f584d7159b8ada03586e65ad9c
                969f6aecbfe44cf356888a7b15a3ff074f771760b26f9c04884ee1faa329fbf4
                e61af23aee7fa5d4d9a5dfcf43c4c26ce8aea2ce8a2990d7ba7b57108b47dabf
                beadb2b25b3cacc1ac0cef346cbb90fb044beee4fac2603a442bdf7e507243b7
                319c9944b1586e899d431c7f91bcccc8690dbf59b28386b2315f3d36ef2eaa3c
                f30b2b51f48b71b003dfb08249484201043f65f5a3ef6bbd61ddfee81aca9ce6
                0081262a00000480dcbc9a3da6fbef5c1c0a55e48a0e729f9184fcb1407c3152
                9db268f6fe50032a363c9801306837fafabdf957fd97eafc80dbd165e435d0e2
                dfd836a28b354023924b6fb7e48bc0b3ed95eea64c2d402f4d734c8dc26f3ac5
                91825daef01eae3c38e3328d00a77dc657034f287ccb0f0e1c9a7cbdc828f627
                205e4737b84b58376551d44c12c3c215c812a0970789c83de51d6ad787271963
                327f0a5fbb6b5907dec02c9a90934af5a1c63b72c82653605d1dcce51596b3c2
                b45696689f2eb382007497557692caac4d57b5de9f5569bc2ad0137fd47fb47e
                664fcb6db4971f5b3e07aceda9ac130e9f38182de994cff192ec0e82fd6d4cb7
                f3fe00812589b7a7ce515440456433016b84a59bec6619a1c6c0b37dd1450ed4
                f2d8b584410ceda8025f5d2d8dd0d2176fc1cf2cc06fa8c82bed4d944e71339e
                ce780fd025bd41ec34ebff9d4270a3224e019fcb444474d482fd2dbe75efb203
                89cc10cd600abb54c47ede93e08c114edb04117d714dc1d525e11bed8756192f
                929d15462b939ff3f52f2252da2ed64d8fae88818b1efa2c7b08c8794fb1b214
                aa233db3162833141ea4383f1a6f120be1db82ce3630b3429114463157a64e91
                234d475e2f79cbf05e4db6a9407d72c6bff7d1198b5c4d6aad2831db61274993
                715a0182c7dc8089e32c8531deed4f7431c07c02195eba2ef91efb5613c37af7
                ae0c066babc69369700e1dd26eddc0d216c781d56e4ce47e3303fa73007ff7b9
                49ef23be2aa4dbf25206fe45c20dd888395b2526391a724996a44156beac8082
                12858792bf8e74cba49dee5e8812e019da87454bff9e847ed83db07af3137430
                82f880a278f682c2bd0ad6887cb59f652e155987d61bbf6a88d36ee93b6072e6
                656d9ccbaae3d655852e38deb3a2dcf8058dc9fb6f2ab3d3b3539eb77b248a66
                1091d05eb6e2f297774fe6053598457cc61908318de4b826f0fc86d4bb117d33
                e865aa805009cc2918d9c2f840c4da43a703ad9f5b5806163d7161696b5a0adc
                00000005
                d5c0d1bebb06048ed6fe2ef2c6cef305b3ed633941ebc8b3bec9738754cddd60
                e1920ada52f43d055b5031cee6192520d6a5115514851ce7fd448d4a39fae2ab
                2335b525f484e9b40d6a4a969394843bdcf6d14c48e8015e08ab92662c05c6e9
                f90b65a7a6201689999f32bfd368e5e3ec9cb70ac7b8399003f175c40885081a
                09ab3034911fe125631051df0408b3946b0bde790911e8978ba07dd56c73e7ee
                """)
        ),

        // Test Case #18
        // LMS signature is too short
        new TestCase(
            new SignatureException(),
            false, // expected result
            decode("""
                00000002
                00000005
                00000004
                61a5d57d37f5e46bfb7520806b07a1b850650e3b31fe4a773ea29a07f09cf2ea
                30e579f0df58ef8e298da0434cb2b878"""),
            decode("""
                54686520706f77657273206e6f742064656c65676174656420746f2074686520
                556e69746564205374617465732062792074686520436f6e737469747574696f
                6e2c206e6f722070726f6869626974656420627920697420746f207468652053
                74617465732c2061726520726573657276656420746f20746865205374617465
                7320726573706563746976656c792c206f7220746f207468652070656f706c65
                2e0a"""),
            decode("""
                00000001
                00000005
                00000004
                // very short
                d32b56671d7eb98833c49b433c272586bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb6""")
        ),

        // Test Case #19
        // bad signature
        new TestCase(
            null,  // exception
            false, // expected result
            decode("""
                00000002
                00000005
                00000004
                71a5d57d37f5e46bfb7520806b07a1b8 // 61 changed to 71
                50650e3b31fe4a773ea29a07f09cf2ea30e579f0df58ef8e298da0434cb2b878
                """),
            decode("""
                54686520706f77657273206e6f742064656c65676174656420746f2074686520
                556e69746564205374617465732062792074686520436f6e737469747574696f
                6e2c206e6f722070726f6869626974656420627920697420746f207468652053
                74617465732c2061726520726573657276656420746f20746865205374617465
                7320726573706563746976656c792c206f7220746f207468652070656f706c65
                2e0a"""),
            decode("""
                00000001
                00000005
                00000004
                d32b56671d7eb98833c49b433c272586bc4a1c8a8970528ffa04b966f9426eb9
                965a25bfd37f196b9073f3d4a232feb69128ec45146f86292f9dff9610a7bf95
                a64c7f60f6261a62043f86c70324b7707f5b4a8a6e19c114c7be866d488778a0
                e05fd5c6509a6e61d559cf1a77a970de927d60c70d3de31a7fa0100994e162a2
                582e8ff1b10cd99d4e8e413ef469559f7d7ed12c838342f9b9c96b83a4943d16
                81d84b15357ff48ca579f19f5e71f18466f2bbef4bf660c2518eb20de2f66e3b
                14784269d7d876f5d35d3fbfc7039a462c716bb9f6891a7f41ad133e9e1f6d95
                60b960e7777c52f060492f2d7c660e1471e07e72655562035abc9a701b473ecb
                c3943c6b9c4f2405a3cb8bf8a691ca51d3f6ad2f428bab6f3a30f55dd9625563
                f0a75ee390e385e3ae0b906961ecf41ae073a0590c2eb6204f44831c26dd768c
                35b167b28ce8dc988a3748255230cef99ebf14e730632f27414489808afab1d1
                e783ed04516de012498682212b07810579b250365941bcc98142da13609e9768
                aaf65de7620dabec29eb82a17fde35af15ad238c73f81bdb8dec2fc0e7f93270
                1099762b37f43c4a3c20010a3d72e2f606be108d310e639f09ce7286800d9ef8
                a1a40281cc5a7ea98d2adc7c7400c2fe5a101552df4e3cccfd0cbf2ddf5dc677
                9cbbc68fee0c3efe4ec22b83a2caa3e48e0809a0a750b73ccdcf3c79e6580c15
                4f8a58f7f24335eec5c5eb5e0cf01dcf4439424095fceb077f66ded5bec73b27
                c5b9f64a2a9af2f07c05e99e5cf80f00252e39db32f6c19674f190c9fbc506d8
                26857713afd2ca6bb85cd8c107347552f30575a5417816ab4db3f603f2df56fb
                c413e7d0acd8bdd81352b2471fc1bc4f1ef296fea1220403466b1afe78b94f7e
                cf7cc62fb92be14f18c2192384ebceaf8801afdf947f698ce9c6ceb696ed70e9
                e87b0144417e8d7baf25eb5f70f09f016fc925b4db048ab8d8cb2a661ce3b57a
                da67571f5dd546fc22cb1f97e0ebd1a65926b1234fd04f171cf469c76b884cf3
                115cce6f792cc84e36da58960c5f1d760f32c12faef477e94c92eb75625b6a37
                1efc72d60ca5e908b3a7dd69fef0249150e3eebdfed39cbdc3ce9704882a2072
                c75e13527b7a581a556168783dc1e97545e31865ddc46b3c957835da252bb732
                8d3ee2062445dfb85ef8c35f8e1f3371af34023cef626e0af1e0bc017351aae2
                ab8f5c612ead0b729a1d059d02bfe18efa971b7300e882360a93b025ff97e9e0
                eec0f3f3f13039a17f88b0cf808f488431606cb13f9241f40f44e537d302c64a
                4f1f4ab949b9feefadcb71ab50ef27d6d6ca8510f150c85fb525bf25703df720
                9b6066f09c37280d59128d2f0f637c7d7d7fad4ed1c1ea04e628d221e3d8db77
                b7c878c9411cafc5071a34a00f4cf07738912753dfce48f07576f0d4f94f42c6
                d76f7ce973e9367095ba7e9a3649b7f461d9f9ac1332a4d1044c96aefee67676
                401b64457c54d65fef6500c59cdfb69af7b6dddfcb0f086278dd8ad0686078df
                b0f3f79cd893d314168648499898fbc0ced5f95b74e8ff14d735cdea968bee74
                00000005
                d8b8112f9200a5e50c4a262165bd342cd800b8496810bc716277435ac376728d
                129ac6eda839a6f357b5a04387c5ce97382a78f2a4372917eefcbf93f63bb591
                12f5dbe400bd49e4501e859f885bf0736e90a509b30a26bfac8c17b5991c157e
                b5971115aa39efd8d564a6b90282c3168af2d30ef89d51bf14654510a12b8a14
                4cca1848cf7da59cc2b3d9d0692dd2a20ba3863480e25b1b85ee860c62bf5136
                00000005
                00000004
                d2f14ff6346af964569f7d6cb880a1b66c5004917da6eafe4d9ef6c6407b3db0
                e5485b122d9ebe15cda93cfec582d7ab
                0000000a
                00000004
                0703c491e7558b35011ece3592eaa5da4d918786771233e8353bc4f62323185c
                95cae05b899e35dffd717054706209988ebfdf6e37960bb5c38d7657e8bffeef
                9bc042da4b4525650485c66d0ce19b317587c6ba4bffcc428e25d08931e72dfb
                6a120c5612344258b85efdb7db1db9e1865a73caf96557eb39ed3e3f426933ac
                9eeddb03a1d2374af7bf77185577456237f9de2d60113c23f846df26fa942008
                a698994c0827d90e86d43e0df7f4bfcdb09b86a373b98288b7094ad81a0185ac
                100e4f2c5fc38c003c1ab6fea479eb2f5ebe48f584d7159b8ada03586e65ad9c
                969f6aecbfe44cf356888a7b15a3ff074f771760b26f9c04884ee1faa329fbf4
                e61af23aee7fa5d4d9a5dfcf43c4c26ce8aea2ce8a2990d7ba7b57108b47dabf
                beadb2b25b3cacc1ac0cef346cbb90fb044beee4fac2603a442bdf7e507243b7
                319c9944b1586e899d431c7f91bcccc8690dbf59b28386b2315f3d36ef2eaa3c
                f30b2b51f48b71b003dfb08249484201043f65f5a3ef6bbd61ddfee81aca9ce6
                0081262a00000480dcbc9a3da6fbef5c1c0a55e48a0e729f9184fcb1407c3152
                9db268f6fe50032a363c9801306837fafabdf957fd97eafc80dbd165e435d0e2
                dfd836a28b354023924b6fb7e48bc0b3ed95eea64c2d402f4d734c8dc26f3ac5
                91825daef01eae3c38e3328d00a77dc657034f287ccb0f0e1c9a7cbdc828f627
                205e4737b84b58376551d44c12c3c215c812a0970789c83de51d6ad787271963
                327f0a5fbb6b5907dec02c9a90934af5a1c63b72c82653605d1dcce51596b3c2
                b45696689f2eb382007497557692caac4d57b5de9f5569bc2ad0137fd47fb47e
                664fcb6db4971f5b3e07aceda9ac130e9f38182de994cff192ec0e82fd6d4cb7
                f3fe00812589b7a7ce515440456433016b84a59bec6619a1c6c0b37dd1450ed4
                f2d8b584410ceda8025f5d2d8dd0d2176fc1cf2cc06fa8c82bed4d944e71339e
                ce780fd025bd41ec34ebff9d4270a3224e019fcb444474d482fd2dbe75efb203
                89cc10cd600abb54c47ede93e08c114edb04117d714dc1d525e11bed8756192f
                929d15462b939ff3f52f2252da2ed64d8fae88818b1efa2c7b08c8794fb1b214
                aa233db3162833141ea4383f1a6f120be1db82ce3630b3429114463157a64e91
                234d475e2f79cbf05e4db6a9407d72c6bff7d1198b5c4d6aad2831db61274993
                715a0182c7dc8089e32c8531deed4f7431c07c02195eba2ef91efb5613c37af7
                ae0c066babc69369700e1dd26eddc0d216c781d56e4ce47e3303fa73007ff7b9
                49ef23be2aa4dbf25206fe45c20dd888395b2526391a724996a44156beac8082
                12858792bf8e74cba49dee5e8812e019da87454bff9e847ed83db07af3137430
                82f880a278f682c2bd0ad6887cb59f652e155987d61bbf6a88d36ee93b6072e6
                656d9ccbaae3d655852e38deb3a2dcf8058dc9fb6f2ab3d3b3539eb77b248a66
                1091d05eb6e2f297774fe6053598457cc61908318de4b826f0fc86d4bb117d33
                e865aa805009cc2918d9c2f840c4da43a703ad9f5b5806163d7161696b5a0adc
                00000005
                d5c0d1bebb06048ed6fe2ef2c6cef305b3ed633941ebc8b3bec9738754cddd60
                e1920ada52f43d055b5031cee6192520d6a5115514851ce7fd448d4a39fae2ab
                2335b525f484e9b40d6a4a969394843bdcf6d14c48e8015e08ab92662c05c6e9
                f90b65a7a6201689999f32bfd368e5e3ec9cb70ac7b8399003f175c40885081a
                09ab3034911fe125631051df0408b3946b0bde790911e8978ba07dd56c73e7ee
                """)
        )
    };
}
