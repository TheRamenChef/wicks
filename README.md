# wicks

### This tool is still pending approval from the TVTropes staff, and is mostly untested. Do not use it before those issues are resolved.

This is a command-line tool to help make wick checks on [TVTropes](https://tvtropes.org). To use it, enter:

    java WickCheck [WikiWord]

Where `[WikiWord]` is a [WikiWord](https://tvtropes.org/pmwiki/pmwiki.php/Main/WikiWord) pointing to the desired article. Curly braces are not only not required, but are not recognized by the tool. The program will download the &ldquo;Related&rdquo; page for the specified article, select an appropriate number of wicks for you to check, and display the relevant sections of the articles' sources to be reviewed. Alternatively, you can use `-c [count]` to check a specific number of wicks.

For each wick selected, the program will display the source for the paragraph or bullet containing the link, as well as that of any parent bullets, to the console. From there, you can enter `m` to mark it as misuse, `c` to mark it as correct usage, or `z` if you don't have enough context to determine misuse (e.g. a zero-context example or a pothole). After you have gone through all of the wicks, the program will generate a summary for the wick check, ready to insert into a trope repair shop post. By default, it prints this to the console, but you can specify a file to save it to after the article.