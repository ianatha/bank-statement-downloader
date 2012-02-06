(ns machocfo.wells-fargo)

(def web-client
	(doto
		(new com.gargoylesoftware.htmlunit.WebClient)
		(.setJavaScriptEnabled false)
		(.setCssEnabled false)))

(defn assert-page-title [page & title-regexes]
	{:post (not-any? nil? (map #(re-matches % (.getTitleText page)) title-regexes))}
	page)

(defn page [url & title-regex]
	(assert-page-title (.getPage web-client url) title-regex))

(defn login-page [username password]
	(let [
		homepage (page "https://www.wellsfargo.com" #"Wells Fargo - .*")
		signon-form (doto (.getFormByName homepage "signon")
						(.. (getInputByName "userid") (setValueAttribute username))
						(.. (getInputByName "password") (setValueAttribute password)))
		] (assert-page-title (.click (.getInputByName signon-form "btnSignon")) #"Wells Fargo Account Summary")))

(defn statements-page [login-page]
	(assert-page-title
		(.. login-page (getAnchorByText "Statements & Documents") (click))
		#"Wells Fargo Statements & Documents"))

(defn statement-years [statements-page]
	(filter
		#(re-matches #"(19|2[0-9])[0-9][0-9]" %)
		(map #(.getTextContent %) (.getAnchors statements-page))))

(defn statements-per-year-page [statements-page year]
	(assert-page-title
		(.. statements-page (getAnchorByText year) (click))
		#"Wells Fargo Statements & Documents"))

(defn statements [statements-page year]
	(filter 
		#(not (nil? %))
		(map 
			#(get (re-matches #"(?s)Statement.*([01][0-9]/[01][0-9]/[0-9][0-9]).*\([0-9]*K\)" %) 1)
			(map #(.getTextContent %) (.getAnchors (statements-per-year-page statements-page year))))))

(defn pdf-statement [statements-page year statement-name]
	(let [
		year-page (statements-per-year-page statements-page year)
		pdf-statement-page (fn []
			{:post [(instance? com.gargoylesoftware.htmlunit.UnexpectedPage %)]}
			(.click
				(first
					(filter
						#(re-matches (re-pattern (str "(?s)Statement.*" statement-name ".*")) (.getTextContent %))
						(.getAnchors year-page)))))
		] (.getInputStream (pdf-statement-page))))

(defn pdf-statement-to-lines [pdf-statement-inputstream]
	(let [
		reader (new com.itextpdf.text.pdf.PdfReader pdf-statement-inputstream)
		; for (int i = 1; i <= reader.getNumberOfPages(); i++) {
		; System.out.println(format("<page n=\"%d\">",i));
		; final List<TextRenderInfo> texts = Lists.newArrayList();

		; RenderListener listener = new RenderListener() {
		; 	public void renderText(TextRenderInfo arg0) {
		; 		if (arg0.getText().length() > 1) { texts.add(arg0); }
		; 	}

		; 	public void renderImage(ImageRenderInfo arg0) {}
		; 	public void endTextBlock() {}
		; 	public void beginTextBlock() {}
		; };
		; PdfContentStreamProcessor processor = new PdfContentStreamProcessor(listener);
		; PdfDictionary resourcesDictionary = reader.getPageN(i).getAsDict(PdfName.RESOURCES);
		; processor.processContent(ContentByteUtils.getContentBytesForPage(reader, i), resourcesDictionary);
		; return lines
		; Multimap<Float, TextRenderInfo> b = Multimaps.index(texts, new Function<TextRenderInfo, Float>() {
		; 	public Float apply(TextRenderInfo arg0) {
		; 		return arg0.getBaseline().getBoundingRectange().y;
		; 	}
		; });

		; for (Float lineY : Ordering.natural().reverse().sortedCopy(b.keySet())) {
		; 	Ordering<TextRenderInfo> xordering = new Ordering<TextRenderInfo>() {
		; 		@Override
		; 		public int compare(TextRenderInfo arg0, TextRenderInfo arg1) {
		; 			return Ordering.natural().compare(arg1.getBaseline().getBoundingRectange().x,
		; 			    arg1.getBaseline().getBoundingRectange().x);
		; 		}

		; 	};
		; 	for (TextRenderInfo text : xordering.sortedCopy(b.get(lineY))) {
		; 		System.out.print(text.getText() + " ");
		; 	}
		; 	System.out.println();
		; }		
		] reader))

(defn -main [username password]
  (let [
	  login-page (login-page username password)
	  statements-page (statements-page login-page)
	  statement-years (statement-years statements-page)
	  statements (zipmap statement-years (map #(statements statements-page %) statement-years))
	  ] statements))