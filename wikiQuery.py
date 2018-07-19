from wikiLib import summary, exceptions

def queryWikipedia(query):

    try:
        result = summary(query, sentences=3)

    except (exceptions.PageError, exceptions.DisambiguationError):
        result = ''

    return result 
