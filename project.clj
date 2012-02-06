(defproject machocfo "1.0.0-SNAPSHOT"
  :description "(jsonify (extract-transactions (statements (bank :wells-fargo))))"
  :main machocfo.wells-fargo
  :dependencies [
  	[org.clojure/clojure "1.2.1"]
  	[com.itextpdf/itextpdf "5.1.3"]
  	[net.sourceforge.htmlunit/htmlunit "2.9"]])