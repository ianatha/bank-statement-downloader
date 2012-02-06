(ns machocfo.wells-fargo
	(:import
		(com.itextpdf.text.pdf.parser TextRenderInfo RenderListener PdfContentStreamProcessor ContentByteUtils)
		(com.itextpdf.text.pdf PdfName)
		(com.google.common.collect Multimaps)
		(com.google.common.base Function)))

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

(defn pdf-statement-to-text-chunks [pdf-statement-inputstream]
	(let [
		reader (new com.itextpdf.text.pdf.PdfReader pdf-statement-inputstream)
		pages (range 1 (inc (.getNumberOfPages reader)))
		page-to-chunks (fn [page]
			(let [
				chunks (new java.util.LinkedList)
				listener (proxy [RenderListener] []
								(renderText [#^TextRenderInfo chunk] (. chunks add chunk))
								(beginTextBlock nil)
								(endTextBlock nil)
								(renderImage [#^ImageRenderInfo chunk] nil))
				processor (new PdfContentStreamProcessor listener)
				resources (.. reader (getPageN page) (getAsDict PdfName/RESOURCES))
				bytes (ContentByteUtils/getContentBytesForPage reader page)
				processorExecution (.. processor (processContent bytes resources))
				] chunks)) 
		] (map page-to-chunks pages)))

(defn text-chunks-to-lines [chunks]
	chunks)

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
	; }		)


(defn -main [username password]
  (let [
	  login-page (login-page username password)
	  statements-page (statements-page login-page)
	  statement-years (statement-years statements-page)
	  statements (zipmap statement-years (map #(statements statements-page %) statement-years))
	  pdfs (map #(text-chunks-to-lines
		  			(pdf-statement-to-text-chunks
			  			(pdf-statement statements-page "2005" %)))
			  	(statements "2005"))
	  ] pdfs))