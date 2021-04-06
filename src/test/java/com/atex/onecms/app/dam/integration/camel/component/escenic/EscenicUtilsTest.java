package com.atex.onecms.app.dam.integration.camel.component.escenic;

import org.junit.Test;

import static org.junit.Assert.*;

public class EscenicUtilsTest {
    EscenicUtils eu = new EscenicUtils();

    @Test
    public void extractOvermatterAndNotesTags() {
        String[] tests =
                {
                        "<p>UCD distances itself from anti-lockdown professor who<span class=\"x-atex-overmatter\"> spoke at Dublin protest</span></p>",
                        "<p>This is my para<span class=\"x-atex-overmatter\"> <a href=\"#link\">bold text</a></span>",
                        "<p>bold italic long sentence too long for a single line&nbsp;far too long it really is <span class=\"x-atex-overmatter\">far too long</span></p>",
                        "line one\nline two",
                        "<p>this headline</p>\n" +
                                "<p>is in two parts but is made far too <span class=\"x-atex-overmatter\">long</span></p>",
                        "<p>two line headline</p>\n" +
                                "\n" +
                                "<p>not too long though</p>",
                        "<p><em><strong>bold italic block quote</strong></em> http://www.atex.com yhuiktfouvo uyvgioyutfi<span class=\"x-atex-overmatter\"> oviytdujtyrcikyt vyiktcy</span></p>",
                        "<div style=\"display: none;\"></div>\\n\\n<p>lead</p>\\n\\n<p><script type=\"text/atex-note\">note-0</script>&nbsp;</p>\\n\\n<p><script type=\"text/atex-note\">note-1</script></p>\\n\\n<p><script type=\"text/atex-note\">note-2</script> dasdasdasdij j</p>\\n\\n<p><script type=\"text/atex-note\">note-3</script>test</p>\\n\\n<p><script type=\"text/atex-note\">note-4</script>&nbsp;</p>\\n\\n<p>test body text<script type=\"text/atex-note\">note-5</script></p>\\n\\n<p><script type=\"text/atex-note\">note-6</script>normal text</p>\\n\\n<p>test</p>\\n\\n<p><script type=\"text/atex-note\">note-7</script></p>\\n\\n<p>test</p>\\n\\n<p><script type=\"text/atex-note\">note-8</script>&nbsp;</p>\\n\\n<p><script type=\"text/atex-note\">note-9</script>&nbsp;</p>\\n"
                };
        String[] expected =
                {
                        "<p>UCD distances itself from anti-lockdown professor who spoke at Dublin protest</p>",
                        "<p>This is my para <a href=\"#link\">bold text</a></p>",
                        "<p>bold italic long sentence too long for a single line far too long it really is far too long</p>",
                        "line one\nline two",
                        "<p>this headline</p>\n" +
                                "<p>is in two parts but is made far too long</p>",
                        "<p>two line headline</p>\n" +
                                "\n" +
                                "<p>not too long though</p>",
                        "<p><em><strong>bold italic block quote</strong></em> http://www.atex.com yhuiktfouvo uyvgioyutfi oviytdujtyrcikyt vyiktcy</p>",
                        "<div style=\"display: none;\"></div>\\n\\n<p>lead</p>\\n\\n<p> </p>\\n\\n<p></p>\\n\\n<p> dasdasdasdij j</p>\\n\\n<p>test</p>\\n\\n<p> </p>\\n\\n<p>test body text</p>\\n\\n<p>normal text</p>\\n\\n<p>test</p>\\n\\n<p></p>\\n\\n<p>test</p>\\n\\n<p> </p>\\n\\n<p> </p>\\n"
                };

        for (int i = 0; i < tests.length; i++) {
            String document = eu.processAndReplaceOvermatterAndNoteTags(tests[i]);
            assertEquals("processing testing #" + i, expected[i], document);
        }

    }

    @Test
    public void getFirstBodyParagraph() {
        String test = "<div style=\"display: none;\">&nbsp;</div>\n\n<p>These are some cute baby animals:</p>\n\n<div data-oembed-url=\"https://www.youtube.com/watch?v=NGC8IS4gjpM&amp;feature=youtu.be\">\n<div style=\"left: 0; width: 100%; height: 0; position: relative; padding-bottom: 56.25%;\"><iframe allow=\"encrypted-media; accelerometer; gyroscope; picture-in-picture\" allowfullscreen=\"\" scrolling=\"no\" src=\"https://www.youtube.com/embed/NGC8IS4gjpM?rel=0\" style=\"border: 0; top: 0; left: 0; width: 100%; height: 100%; position: absolute;\" tabindex=\"-1\"></iframe></div>\n</div>\n\n<p>This is a tweet</p>\n\n<div data-oembed-url=\"https://twitter.com/AnimalsAreCuteX/status/1192569315805073408\">\n<div style=\"max-width:320px;margin:auto;\"><!-- You're using demo endpoint of Iframely API commercially. Max-width is limited to 320px. Please get your own API key at https://iframely.com. -->\n<blockquote align=\"center\" class=\"twitter-tweet\" data-dnt=\"true\">\n<p dir=\"ltr\" lang=\"en\">Tiny monster 🐱 <a href=\"https://t.co/Z6WKkb5P7D\">pic.twitter.com/Z6WKkb5P7D</a></p>\n— Animals are cute 💕 (@AnimalsAreCuteX) <a href=\"https://twitter.com/AnimalsAreCuteX/status/1192569315805073408?ref_src=twsrc%5Etfw\">November 7, 2019</a></blockquote>\n<script async=\"\" charset=\"utf-8\" src=\"https://platform.twitter.com/widgets.js\"></script></div>\n</div>\n\n<p>&nbsp;</p>\n\n<p>And another tweet (which is the same)</p>\n\n<div data-oembed-url=\"https://twitter.com/AnimalsAreCuteX/status/1192569315805073408\">\n<div style=\"max-width:320px;margin:auto;\"><!-- You're using demo endpoint of Iframely API commercially. Max-width is limited to 320px. Please get your own API key at https://iframely.com. -->\n<blockquote align=\"center\" class=\"twitter-tweet\" data-dnt=\"true\">\n<p dir=\"ltr\" lang=\"en\">Tiny monster 🐱 <a href=\"https://t.co/Z6WKkb5P7D\">pic.twitter.com/Z6WKkb5P7D</a></p>\n— Animals are cute 💕 (@AnimalsAreCuteX) <a href=\"https://twitter.com/AnimalsAreCuteX/status/1192569315805073408?ref_src=twsrc%5Etfw\">November 7, 2019</a></blockquote>\n<script async=\"\" charset=\"utf-8\" src=\"https://platform.twitter.com/widgets.js\"></script></div>\n</div>\n\n<p>&nbsp;</p>\n\n<p>And another, different tweet</p>\n\n<div data-oembed-url=\"https://twitter.com/AnimalsAreCuteX/status/1190643392658714625?s=20\">\n<div style=\"max-width:320px;margin:auto;\"><!-- You're using demo endpoint of Iframely API commercially. Max-width is limited to 320px. Please get your own API key at https://iframely.com. -->\n<blockquote align=\"center\" class=\"twitter-tweet\" data-dnt=\"true\">\n<p dir=\"ltr\" lang=\"fr\">Best camouflage 🤔 <a href=\"https://t.co/jIQLZGu56X\">pic.twitter.com/jIQLZGu56X</a></p>\n— Animals are cute 💕 (@AnimalsAreCuteX) <a href=\"https://twitter.com/AnimalsAreCuteX/status/1190643392658714625?ref_src=twsrc%5Etfw\">November 2, 2019</a></blockquote>\n<script async=\"\" charset=\"utf-8\" src=\"https://platform.twitter.com/widgets.js\"></script></div>\n</div>\n\n<p>Let’s do this!</p>\n";
        assertEquals("These are some cute baby animals:", eu.getFirstBodyParagraph(test));
    }

    @Test
    public void removeHtmlTagsTest() {
        String[] tests = {
                "<p><em><strong>bold italic block quote</strong></em> http://www.atex.com yhuiktfouvo uyvgioyutfi<span class=\\\"x-atex-overmatter\\\"> oviytdujtyrcikyt vyiktcy</span></p>",
                "<html><body><p>this is a paragraph</p></body></html>",
                "<p>This is my para<span class=\"x-atex-overmatter\"> text <a href=\"#link\">bold text</a></span>"
        };
        String[] expected = {
                "bold italic block quote http://www.atex.com yhuiktfouvo uyvgioyutfi oviytdujtyrcikyt vyiktcy",
                "this is a paragraph",
                "This is my para text bold text"
        };
        for (int i = 0; i < tests.length; i++) {
            String result = eu.removeHtmlTags(tests[i]);
            assertEquals(expected[i], result);
        }
    }

}