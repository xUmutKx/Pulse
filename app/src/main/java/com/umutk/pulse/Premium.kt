package com.umutk.pulse

import android.content.Context
import android.os.SystemClock
import java.security.MessageDigest

/**
 * Pulse Premium, unlocked with a promo code. Only the SHA-256 of each code (with a salt) is in the app and in the repository: a code is 16 random
 * Crockford base-32 letters (80 bits), so it cannot be worked out from its hash or found by trying. Five wrong codes in a row lock the field for
 * 15 minutes, and every lock after that doubles (up to a day); the lock counts real time since boot and wall time, so changing the clock does not lift it.
 * The Play build does not offer codes: Google Play wants paid features unlocked through Play Billing (its own promo codes).
 */
object Premium {
    private const val SALT = "pulse-premium-v1:"
    private const val MAX_TRIES = 5
    private fun sp(c: Context) = c.getSharedPreferences("pulse_premium", Context.MODE_PRIVATE)

    /** Codes are offered in this build (not in the Play build). */
    val codesOffered: Boolean get() = BuildConfig.FLAVOR != "play"

    fun isOn(c: Context): Boolean = sp(c).getString("code", null)?.let { it in HASHES } == true

    sealed class Result {
        object Ok : Result()
        class Wrong(val left: Int) : Result()
        class Locked(val minutes: Long) : Result()
    }

    /** Minutes left of a lock, 0 when codes may be tried. */
    fun lockedMinutes(c: Context): Long {
        val p = sp(c)
        val until = p.getLong("until_wall", 0L)
        val left = until - System.currentTimeMillis()
        // a clock moved back still waits: the time since boot is checked too while the phone was not restarted
        val bootUntil = p.getLong("until_boot", 0L)
        val leftBoot = if (bootUntil > 0 && SystemClock.elapsedRealtime() < bootUntil + 1) bootUntil - SystemClock.elapsedRealtime() else 0L
        val ms = maxOf(left, leftBoot)
        return if (ms <= 0) 0 else (ms + 59_999) / 60_000
    }

    fun redeem(c: Context, input: String): Result {
        lockedMinutes(c).let { if (it > 0) return Result.Locked(it) }
        val p = sp(c)
        // the letters after "PLS", with the look-alikes read as Crockford does (O is 0, I and L are 1)
        val norm = input.uppercase().filter { it.isLetterOrDigit() }.removePrefix("PLS").replace('O', '0').replace('I', '1').replace('L', '1')
        val h = sha(SALT + norm)
        if (h in HASHES) {
            p.edit().putString("code", h).putInt("fails", 0).putInt("locks", 0).apply()
            return Result.Ok
        }
        val fails = p.getInt("fails", 0) + 1
        if (fails >= MAX_TRIES) {
            val locks = p.getInt("locks", 0)
            val ms = (15L * 60_000 shl locks.coerceAtMost(6)).coerceAtMost(24L * 3_600_000)
            p.edit().putInt("fails", 0).putInt("locks", locks + 1).putLong("until_wall", System.currentTimeMillis() + ms).putLong("until_boot", SystemClock.elapsedRealtime() + ms).apply()
            return Result.Locked((ms + 59_999) / 60_000)
        }
        p.edit().putInt("fails", fails).apply()
        return Result.Wrong(MAX_TRIES - fails)
    }

    private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // the 200 codes, hashed; the codes themselves are not in the repository
    private val HASHES: Set<String> = hashSetOf(
        "007114a721a66cada85049f855f208be9cfbdfd6e4c4187d7453a04c96243cce",
        "06377be30c80a75634b5eb819b32890958c89f35479d35212f3ab1e3916dff89",
        "07266018e1562a40ee86fa25cd2dca2396700c55bb326848520b3b3868502299",
        "07bed67d55209e27f60d2fef628e613245f00eff8e7678b34a8cabb7329a5217",
        "089f68d09b2fd622f3c239336ff3ec06d1d09c1117e1bd90a0c5eb639a0d4c26",
        "08d773f677b455292d2e4a0c4ce84c07cca8ef16c9ac6ac003444386b0a88ddd",
        "08ee28497f8a3e4cd3549218186d146a32e5e43e81b99ef986670a30cdc10894",
        "0bb1f5d29e32d1af57975c89fd9d1a92562aa806bb28580a8444adb710a66573",
        "0c289f2fae99cc2c598d89d139df73557519e70974116c445c2e47a3291c6537",
        "0ce0a4a19dc4fbd1a86c2c2088516668dd0706b44c93129c913c9677aff5a054",
        "100400ff2d1a629fc40d6261b35e9a4cd2e4229141b8e48e7f6300c62a5dd123",
        "11ce2d5b787b4f26f190d916702aea8b22550d985cecc9b3547446e954aed316",
        "137145a16326cec489a4671487e8d76451a4518eae959048b030d8c368833af3",
        "14c3ebdf3ab3009e7b4eed14d6dd45d829402441fe7f2c9926f36c825c5c1adf",
        "16d6687e391090a985271f1f2d87f5845d3d0f98ba48d3859d8ee80d0362ae88",
        "19138aaa0a05f79fed6d35c0f57fc79afb83795c7b16887c63454f04873ce287",
        "1a774a81323ffecf2ef7f6fd23d916fc7ac65db9751c00fa045915c0f303c0a8",
        "1cc1bc9575c919b48360fd4484585494d7a0cf83a278948b51241b4c57925de7",
        "1db40d43b87cb7bbbb3b2dc5b168000f26890997e9923b8b2fde8a33e3448c9d",
        "1de462a2b955a0f1da3b044943eea86a7aee5ed596e8a8b34f2bac5dec35adf7",
        "1de4baa41f16c9675aa4a795da325b3df70d59cadd3b7fa8b1ca795612fe2975",
        "1def2a4b66523d1d3a4d751bb7e9456c4cf4ae69a0dc6411a1f737f4244b0362",
        "23b6ee95d69cda0968cb73d2b42d148f82e309f893029b151358993d4c59cca5",
        "252f6b59388896332a5410a555e591c6245bf6d57edede25824938da961a89d5",
        "253654d6e506b0658da71446e953cc708334f870a67c179eb46d56b2d211b74f",
        "263ca567108a6115f685f547ddaddde99648f33ed4ee6c8e610ec3726c12f680",
        "266fbec7f475ac016490fa803f139e85a34e34e3733d2034d2f9a24f74b2eb04",
        "27536acdb6e0088f09f16c04daa1d8482a316870758583f634caebfc964f1225",
        "28ad2b6917551de62e7c3fdf9c163797dccde517ed10dcd1709a528802656f1d",
        "2bb27de8b6005a2aefa4df8079bd6023e628791929a630d6da903927b3c3e1a2",
        "2d1ba91c0fad473103077543a70f542bad0e71a5216830abafbd2d992e45a5dd",
        "2dff607384fc354770f3836b6c705663fadd88cf26bb2fa917cf33b78edeed6a",
        "2ed0d2437148c21f06b202671802bb8edbbb4d4653b01e4a5973768200656ad7",
        "30f8e2c58f622141e98553ac7a0edfbf5dadf3563d604687a2f336f1cfba7f65",
        "3144ae4102f18426d9839f2e082c08a06d7c2925e4e50ec843418ff182919d4f",
        "327eed466a405fd58779fc512ae182f38507606b6feefee8545aff39f1977b22",
        "3308047e3a8766e13ea35771db77988dc80d6d13725a538323faf465b6416d80",
        "34e83b8caa09110c9417ef99569bf685bb55ccaad52d09626c3c25c8b9771e27",
        "35a574dcda963cb21fcaf819aad6f997d104f72b08d39157c7b0ebef3ce26de3",
        "360ff3d4f08c4ddf34769734482f520cbd00cbaed928dca688902a606a696eb5",
        "3799a87d703c04557354440cda3a7ae12ae0121f543a29f24fed9e33d9f58fde",
        "3bb8eef5a7319c121b9d1c64c2d096933c7d84d8def975d26ab526cd36b9c5bc",
        "3bc8a1a3a26d11b198c66e87f60bdfc3633902ddba8729bb5289276ba7033529",
        "3fc047378eb4cee3512520e9ca38cfa4c9143c2eed749f70369d5eece4d6395b",
        "3fcf0539e3750b51ed2bf37dd1cc5f62b64bd5250fe0b2c0710ed8aa1d2c4000",
        "3fe23753efdc7323ffe5f9de935796ae47adaaad7b673a5f2ec54a7d79399bd0",
        "42bf2b7f31e1a5a8223ac4105e74f2ca76858a9683e09c5ca42598ccd4348646",
        "448949cc4ad725ed05d2c9d3b3b90cb4c1b747b2e739bc767bfc40b11882a180",
        "44c4b95ea181fd4d9d87202212c7426ec03e3dba8080d3619764c37d5f88e17c",
        "46a90e24fb12fa3752300a110b576e4575d2e636c386ea6c13bc4f4781b09661",
        "481e77db2a3256748436eb817fcdedffe0258c3c7bb1cd066063d579bb5a618f",
        "48c9eaba1592c3f4c42b0e585a787fec697651c9f01a56121f7561046dee0f5b",
        "49d996e2865091da05579cbc5ff74759f4a0776e78da98b293c97530718d834f",
        "4d077b38dc8f7acd4ba5d78eb5ce9b3ac69b6a74d679637db5fe69413ae48330",
        "4fb1e6fb655860624866935df48e1b5d2a161e4c3fa0cd9193d29b44880a8cbd",
        "4fd499db79153bda62706bc76bdcb5a6b5d6beb0bb4ffb813f156607b19b7df7",
        "50ac2f06928b4dcc1b3daa5f593a36d6f011ec2fd973a13211da4974e22831f3",
        "50acb86bde04cece70050692bae56c8f59438fb07bac40bf6b24c55596427558",
        "516d39cda9e331362422dee0495e222f01d43f112be6d1aeea3fe5b5e23e0020",
        "51a970639b4a0893f7a842b309167335d8e4391be94c71a69b9f5b5fea0b9b12",
        "555d2dea0b2fb48240af940dbdaf6f5fc856d13ee775eca21d480c510ef5e652",
        "578fa6768c8ecc599c061062ffd7c2fa3adffe5465126b963c4445f2e7f58925",
        "57efd5ecb771e977fcf8282a76bf694f54e57b2af4ce067f4d5e2ff7bcf25d67",
        "58bfc063ef25bec7ffff5fbb17e127e0ec1b2cb68cf6368a8fcf3b99092a579a",
        "5932c61b9613b3f7272a7af33c6644f9520944dc50ce60af15f0d09fc37a4e57",
        "59f863bcb3dd55916e98b52345ef7034b3ae67f3ff16c62737eb962920740100",
        "5c5b797fc1b41b5860359bacfd038f95a5a5e2ad6b392913e77cf4261c3a7ae0",
        "5e6a4a110f8a7b821fa763d5c311c85a8cec14d1b63243c1332c8f85fc8ade70",
        "5fc698747d4c5c0db1304ba4d87e0e5ae9780c0d939b730e8487368ceee983e6",
        "642a17c6794b9354d0069a5d125a496c2a015e288ff0dc88fb5a490c8b4fa553",
        "646b9e496f9180d0988b4bda467be967c07055aee8b24dade65717c7b43cfba1",
        "67c91d751fe7013a9dd1df92408f3774fc170196971b72092b7a5518768ff509",
        "69e3691e287d6761eb73046854b04eb93635758cdc8ed62eecb813f5eaf65d40",
        "69f533439dc0312512eefa10cc84aa41f307a603b0ccb65694e4045f9a43b354",
        "6ad3539b3fc73a814b71fa398668db85e93f1606d28190d543f28a18429293ba",
        "6b9cd366365ebfbf3ba9496d3a0a96be588237e6b30b0377a919291360775f5d",
        "6d2c57e0c53077b4ede017e23a426b94f49b5ea57e328b611397e9b4f1856dd1",
        "6ea23836183a876687788d6324e309228117f10f63059a3df8c801a221350ea0",
        "6ef4e1513a1ee81f4c10e08c875aeb43636d7f8b44ede1a2cd34f74c73c381bc",
        "7045673e0def70447da9fa03885b421d882263ea4aabd945726d34273334ab37",
        "70e3a539c97592b42ff9615c5d432c0d5f4306f922dfa5dd12bcfe268ce62fe4",
        "71397cb46066b3c339ce1bdff0e43ce3a4c1835ce51a5f53d16e5d134d01856c",
        "71b8aabc45631ab9530a9af1b90afbcb5993d80d53883c5d3ad512745cfd43ed",
        "72351370773e0307afec9c565b1d0b78b52b1461eb174b39b58b93dcc02a080e",
        "747a9e0924f9ec926ef647d12dbe4278c731f485f75c22bd9aa9f979cccd406a",
        "7492c87928fad6aa8d6882f9bd8433c1418f8b261bb770e43a08ea8d41399e6e",
        "74f6c95e6594da80545a65fda362ffcb5525ae88bd6ae6281304056d3864a253",
        "75efa735b88f1133bae27ffcc04eba8c0d377458168f48657d051f4a6358ab96",
        "787ee21bf682e2f00fa90cd3259786480a52b6c501f88a9d4b36f8e600cbdc93",
        "7900d4263ca093b9c60604eff4b2cd767e8d91c0a3413e85cf7f99aef0e30222",
        "7adada2b309dc57afee4b59581f5a06ca7e6041c5b64579a7d2aef16d0215723",
        "7bd986b67c946bd8cbbca238ebfa4838aad9a81b74bb896c01cc708db5bbcbc2",
        "7d8d72f13c57252387c9453ed9c6a77e2ffe70e807c21e16044f822f03e1a94a",
        "7e2a341a47d5e1a2d7e2c1ab5e363dd25e3e4412a7f749e8920a7a4b0fa0c2f8",
        "7ed8a039ef82c28e84df0806b000039de9d4d8f1e27184703b63e3330fb87164",
        "7f6304632e0dd146354cb196a593053868c3fc80a45fd70367e7a6b979a5f111",
        "7fe118f404804530595d290a68e8ef5c4c8854d8f747ee69a77d1ee19a4bc0c9",
        "810e5d387336df951619d9353d6ae5794ac87292d5d1c37dbfa36300c78761bc",
        "81460eb305602bae403f0aed20c595315d461a05d1bcafcf84d2d02ea00e338e",
        "82d0027b84bf9c1ff6b3b25210b1053ca036dbebbc8bd2c2050d2683510c88ca",
        "83580e8a252e60fab392ace8a07758d09a716e6b3f44ebdf609134bc5d4dbc4c",
        "8395faad224d8cb26cdd752043ce302f30c3218c1c7cdf4ad19e301dc23df266",
        "846eb9c5abc72d4d787ba61835b296395a0b7bac7cb6b214a66599d7020e735a",
        "8505ddd959d0d1a147e196e7643d65d3a2089d6606b706a22ffab8b7c4b7857a",
        "85873e1bac17f3bc7521607b1b4bb72c150ecc6b771ff558f40d3bc57a9c5904",
        "8627ea94a74f3b9f4dbdeba9a88cb3a04e2c7f322dc100681c2fb2092a7d9e2d",
        "87c6ee234c114e777ddbbabec44c57e9457378a21644da9b6abcaeea386f5928",
        "88cc6faab5d88df62e4fce1172e2385ee731ec381c95dbd650b308dacdf62078",
        "89a8e15365eb93b1f9687518f32501fc4c6904b3a9d5278c1e76ea088105c4c7",
        "8a695feab3c0033b29131344a12e08491ce4105663fc4d7664844675703e3a39",
        "8ad304c4db4ec98778cbbb786cc7e63cf3e00ddc4f6ada7848f7b959ef16e140",
        "8c7efa7b79033203bc375acc3b0d4dd3ea09956d4ba8c9bff26a551cf202bf1a",
        "8d469f2e8c513a2978dcde5ec534579a2fec0acb1b3d5ce906f418353e28ef07",
        "91ed73c138a51446627507a7608339643abf96b19fd0bcd098efbda70bab58c1",
        "9cacd1585c14b3a9dfb8c0a43f947241b9c48a7d03584c8cf49505a85b39fd54",
        "9e73567dab5e87660640b3271bbc703fcf0128d8f7d579ffb0197d06ada2388c",
        "9f43bfb5d4a5fe8602c42ae3b52ad82634754478f5ab727f000c903d11c57b17",
        "a05c45a6262b8efed471ef5c978c9d89ca1035fd796c6c57f460961c0841be1d",
        "a221b0a47ca2c10d9058b0c61a35bb62099c76782fb3e3ce2fa0fade4202a497",
        "a407b5a5e3d2e8673e118b68b3acb27e2373392508b5ae27e7b668e38d00398c",
        "a42ee2db67e3860785f8b304af2a72cc82b523599f8e5996984cec861c83b565",
        "a4807cc9bcc28791dfb91a94f753c1b5ccccaadf749596f1c507955a976cf169",
        "a7493a6ea05e4b2b178e82743f5204818fac95e4022e1aedd5c7ac5c25f3316e",
        "a789e60f7c365ab0c62cbfcd8d09fd7c7ea38cb7cc696380bcda421411077d0e",
        "a82cad6beda2cfaabccc9ab968683e661502478a4ecb517d2e0ed6616e916a61",
        "a915cc339071375120e036c0ed254b9f7ba65093fb3c0f6fac1f47c95849f9d1",
        "aabadfecfd46aa47187e4bb9489b1a91f8ce51acddb3c007be469530a558450f",
        "ab251c33f533e2b00b2e8192bcc820411a1ca1c63772022bc5c3bfd659e7fb54",
        "ad3c9bc1e307aa589672ba4a6f960a16f71262bef9c08a02548c1393fc989477",
        "ae1cfdc36443c53dc1fb5f2c574a82e83080862efbd621f00511ed57ac346c5a",
        "ae7d3812e8b5005e35f625058f22697022b76c37838df37a6e8cb37fe5eb37cb",
        "ae965b057be24b927f0fa63c212653a3847ccbca3196aa2ee5ef6a9b1dd94cff",
        "aef4fdfaabec08c2420f3724f67b85c223a31c422c54600e36b465d94d83d738",
        "b15724236c795d08ff174d86887eb3d63cc69a71c7843530c150eb5ea347dc92",
        "b1f8cc7583150ea269e09cd8483a4408523d60391b47d92232d0cfcda609d60a",
        "b5775c5eacefdf42d492a62009ff543d726c66dd76724f6aed89e4bc8a698056",
        "b901f0653e42e28271cec8435d5bc4af4f10acd7d63fdace07e44628428f1c40",
        "b9a726e101c5170bc51272fbaba584f353c73cc972f0916cf0707ddd9b1bfe32",
        "ba58d4525c4d69db7ed5cf9bc0191e6e646cbbf09debd1a771ef1a5707942898",
        "bd9275e0bbb7230607c2eb8b7ad4828edc50f3921799c623f82c1ff09726fa11",
        "bdccf9d11b3bd454df4bee5f13c63103e28401c8b2318feb431e3f38562de1b1",
        "be0f5a274704e686f7b853bfed5bc2200b26cb1e3d8d83111821627354bc222c",
        "c0d6ad3214ebdc56041ee8261cc9941b4dc530778d3df3575c13ecf041c099ff",
        "c1182e61a3541e1b6ddee35379de62ce8bbe8996dedf16fdc588fffdc90c75b8",
        "c2bf5ca6ec66f7146ef8d39aa05042b3996f361b1dfbf62b1db59212ef60cff9",
        "c366e128b7f796fe01cf00bb4ff8dde01e9d9ca4bf67fea703ed5ce280332d4a",
        "c3b4433ff4f50c979e127148ddf81a5b0d004e24f3a24fd108aac46d75459ffc",
        "c4b7925807ba2486e343a53a4e5a780c4e0f7568a19d7496f271211e86a82fe4",
        "c6afde273eafbe520fa65df10f69ec73633ca2b96f4db259ddfddef6cdde7008",
        "ca3ca971d9c80abb935ab21f3e8320db0f20625133d24a4a2ac2a9de41decf29",
        "ca7d14d7624fe296b466066f66fdffd0d25638ca390f0908dc24009d2e5913f3",
        "cb04d33158318d5e3c5e4fb1246addba510e5fe599f113abd7331ec53d367001",
        "cbc297d51c48c9a08db705ca88b7fdf19ca7f2518c3fe7173ac22de2edaf1c7b",
        "cc58372f3cc10fcbee7f2adfa3691ee46ca6e0748dfa2c7945a71373027bc5aa",
        "cd9ef3df28bc5cf6da29349545494edb89474000f01022dc0be56562e15b42bd",
        "ce1c2cdd21fe799d9a0b1bb4f213ae95649ca638451052bf639d9a2549f2ddcd",
        "cf33bcd2facd63136dce636d4fc5120df656d3c53f41ed28a34981bc68981b99",
        "d0e012cfc55861dd1e493218ccd064c896fae2e254a94f0a482f3781d13ed938",
        "d12abbfd2b44e2e5cb67aec7e3dbf25b86752dafc9b51b6df0baee678321e265",
        "d187ec535fa49248e49888670ddee40e6f419bbbb33de4e19b7f96d0d970c2a0",
        "d1b3fbdc56e186111959011573ed1fd5726b88f3ae32c7303bf92d79afed0161",
        "d23b8099dd32283fb5246af663efd05de7da106a2790cd62215be47fc75e3ae1",
        "d2b315d9c4b8d8a7cd7818e98a0b6e3c1ab6057008274ea5ef3aa312d5d901d2",
        "d3618059d5f16f5ab999d12b99a3aaa352dd47713d8cb15142d5d3ae50a13228",
        "d4058b92f7487f42df854a0636902fba26d2db9c0f94e4e295242e3c2105dfcd",
        "da3f0291d3337e5cf57741501dcac0590ff0610e3079c1245b5e39da7a00a5c4",
        "dabdb85a3223c8b5116196d2c92dacb37343ce9359352ec4f395d57a4a2e5460",
        "dac82ed1bb752c85d9c1785beb372daafa7032823b8c7921fe3c39b66fba3635",
        "dbc34beb38fd3230505ff6ef82a4aaec2dd17a127fd3fc32b1d5e236fc35c965",
        "dc66c045f1c1fda102164953bbeebe7fdce1dfd7e0cf72ce43ebdc082b819b9a",
        "de845805ae5cd99f3503d88fbdf3eedb11259c67f76415cde4bd2faa87ef7bde",
        "dee3a97a3b3585d5bfa3cce8f91970026629e30df44531bc51544fd705d04163",
        "e05b56aeef8eb57a083045ccbcf2a4a1c8394c488a681249bf55e53bc41cf580",
        "e194eb51165dfc7cd20b9d304d7fd3a82e017c1d57ee4b8eb104921c90f8d8c2",
        "e2ceb40a54bfa25df51ea47b70cce96eafc970ec647a97f2efb99212e71b178d",
        "e2db5637c36811f430413bcc2e72ca2f5859e4ae748d83436c37e6ff790fd18c",
        "e89fc21a9ba72c7f19e4f5f02ffae2062645ae33389f18c9f310166516e8939c",
        "ec6a54c98107af21bd42c9ca1c68eb5d569ef898f3b3ad4bbe31878fe3aa5380",
        "ec9a0f80cf6f1b788c92d34d6b25b19bbb4ca737f1f76f2980bfba4008c3c68a",
        "ece153866940632ed550834315f022bfabdf65d706bbfad05409d6b6a0be021d",
        "ef8ce1fd99ce34fbacb78140fe520fb3685d4d567d9020fda15c100c7758dfc2",
        "f182e6e85f301fb4d1911d68f3f2aa778e7545ccc67e8207cb9c5d44d51c6645",
        "f2329648cd9ef68be21a43a7aad0eece234b7ece4a5add23fab73163e3f7cceb",
        "f2ab0eefe6d843fda95b565ab0b2c4416c9ce73dbc6996803601a0a3d5ad0f76",
        "f38a26de0d5a64796f135f188007e2e2ff01e2b81efbdb227718f3e164583f1c",
        "f4f3c19028f82ecb7a806d80390a8bbe72709c160160acf06e0b2c42346f2aa2",
        "f58c0fba699571253701b4632518e54653ff6acc5ea35f9d6789e9f930271f87",
        "f694b2f9d4580ad8b9538e0ab451128cf6037f520efbb1124c4699d9391e348b",
        "f7017efaa94abea90a0b4d8a6a6deaf4f49bc3d17247318db0f58a3d7ed53e37",
        "f805e1dc8579bdb6bcd90b6d0083461700d20300e9dcbad443e51169364c122d",
        "f88b9de9846dea0a239d3450ecb172f5eae61ee2bcc04aa994d3b38d6015e044",
        "f8a78937df3073b8035bf5875f11b55f6f5dedf0e899cddf6f9af8ace1560f15",
        "f947316be5cbebc78b0d39f7ae6726c72248ff494d0ae1c3e927e4068c7c1f1e",
        "fb3d94cf8fae4538db250b33604f0988d43d3763123702a415a2166f4a98706d",
        "fdb4f0bb916ccc7288ba5da6e85a36478956594910aad1632934435af8db2b50",
        "fe2239c19a9b67ba61415c6a90f877702295ab8357931ee6acabd3d81ce50e0a",
        "fef5cd2af8fbc655f20b2b9a383238d05879f1f674c47cd0daeaa173e1944e5b",
        "ff186422856eff6988c7ab45b0e0d4ccf2e9b587bd140c18c26e69d4a4dfd52c",
        "ff49a9b9c43a322da130bec49d886666619bc6f4f11b9d0ad8dbd5c2ebab2e93",
        "ffe19d8b027cfbbf428395726d915c33b014e4eb28e83e34b20d07d107f4c74c"
    )
}
