# compEnv-extraction
Uses named entity recognition to extract details about a computational environment from academic articles.

The code is in Java and will open as an Eclipse project. It calls a python library (through Jython) called wikipedia. This library must be installed under Python2.

You will also need a set of models trained for the Stanford NLP pipeline that are too big to upload to GitHub. You can get them from http://nlp.stanford.edu/software/stanford-corenlp-full-2018-02-27.zip. The file you need is called stanford-corenlp-3.9.1-models.jar (it is inside the zip file). You should place this in your lib directory and add it to your classpath.
