from google.appengine.ext import ndb

DEFAULT_NAME = 'some_global_string'

# SET NO PARENT KEY, tracks should be in different entity groups.
# We set a parent key on the 'Greetings' to ensure that they are all in the same
# entity group. Queries across the single entity group will be consistent.
# However, the write rate should be limited to ~1/second.

#def _key(key_name=DEFAULT_NAME):
#    '''Constructs a Datastore key for a _ entity with key_name.'''
#    return ndb.Key('KeyNameString', key_name)

class Track(ndb.Model):
    '''Models an individual Track entry.'''
    # TODO add indexed=False to properties that should not be indexed
    track_id = ndb.IntegerProperty()
    author = ndb.UserProperty()
    date = ndb.DateTimeProperty(auto_now_add=True)
    last_changed = ndb.DateTimeProperty(auto_now_add=True)
    start_point = ndb.GeoPtProperty()
    finish_point = ndb.GeoPtProperty()