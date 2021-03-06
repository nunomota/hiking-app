from google.appengine.ext import ndb
import json
import logging
from user import User
import time

# Get an instance of a logger
logger = logging.getLogger(__name__)
            
class Comment(ndb.Model):
    '''Models an individual Comment.'''
    # comment_id is database key
    hike_id = ndb.IntegerProperty()
    owner_id = ndb.IntegerProperty()
    comment_text = ndb.BlobProperty(indexed=False)
    date = ndb.DateTimeProperty(auto_now_add=True)
    requested_id = -1

    def from_json(self, json_string):
        json_object = json.loads(json_string)
        self.hike_id = json_object['hike_id']
        self.owner_id = json_object['user_id']
        self.comment_text = str(json_object['comment_text'])
        if 'date' in json_object:
            self.date = json_object['date']
        return True

    # Parse this into JSON string
    def to_json(self):
        
        user_name = get_user_name(self.owner_id)
        comment_data = {
            'comment_id': self.key.id(),
            'hike_id': self.hike_id,
            'user_id': self.owner_id,
            'date': int(time.mktime(self.date.timetuple())),
            'comment_text': self.comment_text,
            'user_name' : user_name
        }
        return json.dumps(comment_data)


# Factory to turn a json-string into a valid comment object
def build_comment_from_json(json_string):
    c = Comment()
    if(c.from_json(json_string)):
        return c
    return None

def get_user_name(user_id):
    user = ndb.Key(User, user_id).get()
    if not user:
        return ""
    return user.name

