package xyz.quaver.hitomi

import org.junit.Test
import java.io.File
import java.net.URL

class UnitTest {
    @Test
    fun test() {
        val f = File("C:/Users/tom50/Workspace/Pupil/nodir/nodir/asdf.txt")

        f.delete()
    }

    @Test
    fun test_nozomi() {
        val nozomi = fetchNozomi(start = 0, count = 5)

        for (n in nozomi)
            println(n)
    }

    @Test
    fun test_search() {
        val ids = getGalleryIDsForQuery("language:korean").reversed()

        print(ids.size)
    }

    @Test
    fun test_suggestions() {
        val suggestions = getSuggestionsForQuery("language:g")

        print(suggestions)
    }

    @Test
    fun test_doSearch() {
        val r = doSearch("female:loli female:bondage language:korean -male:yaoi -male:guro -female:guro")

        print(r.size)
    }

    @Test
    fun test_getBlock() {
        val galleryBlock = getGalleryBlock(1405716)

        print(galleryBlock)
    }

    @Test
    fun test_getGallery() {
        val gallery = getGallery(1405267)

        print(gallery)
    }

    @Test
    fun test_getReader() {
        val reader = getReader(1404693)

        print(reader)
    }

    @Test
    fun test_hiyobi() {
        print(xyz.quaver.hiyobi.getReader(1415416).size)
    }
}