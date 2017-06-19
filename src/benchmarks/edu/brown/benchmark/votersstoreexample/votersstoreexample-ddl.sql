
-- contestants table holds the contestants numbers (for voting) and names
CREATE TABLE contestants
(
  contestant_number integer     NOT NULL
, contestant_name   varchar(50) NOT NULL
, CONSTRAINT PK_contestants PRIMARY KEY
  (
    contestant_number
  )
);

-- Map of Area Codes and States for geolocation classification of incoming calls
CREATE TABLE area_code_state
(
  area_code smallint   NOT NULL
, state     varchar(2) NOT NULL
, CONSTRAINT PK_area_code_state PRIMARY KEY
  (
    area_code
  )
);

-- votes table holds every valid vote.
--   VoterSStoreExamples are not allowed to submit more than <x> votes, x is passed to client application
CREATE TABLE votes
(
  vote_id            bigint     NOT NULL,
  phone_number       bigint     NOT NULL
, state              varchar(2) NOT NULL -- REFERENCES area_code_state (state)
, contestant_number  integer    NOT NULL REFERENCES contestants (contestant_number)
, created	     timestamp  NOT NULL
, CONSTRAINT PK_votes PRIMARY KEY
  (
    vote_id
  )
-- PARTITION BY ( phone_number )
);

CREATE STREAM proc_one_out
(
  vote_id            bigint     NOT NULL,
  phone_number       bigint     NOT NULL
, state              varchar(2) NOT NULL
, contestant_number  integer    NOT NULL
, created	     timestamp    NOT NULL
, ts		     bigint     NOT NULL
,   WSTART      INTEGER
,   WEND        INTEGER
);

CREATE STREAM proc_two_out
(
  row_id            int     NOT NULL
);

--WINDOWS are created on existing streams
--They share the exact same schema.
--NOTE: Inserting into the parent stream DOES NOT insert into the window.  That must be done explicitly.
--This is a tuple window (ROWS)
CREATE WINDOW trending_leaderboard ON proc_one_out ROWS 100 SLIDE 10;

--This is a time window (RANGE). NOTE: a bigint column named "ts" (timestamp) is required for a time-based window.
CREATE WINDOW trending_leaderboard_time ON proc_one_out RANGE 30 SLIDE 2;


--leaderboard table keeps track of the aggregated number of votes within the trending_leaderboard window
CREATE TABLE leaderboard
(
  contestant_number  integer   NOT NULL
, num_votes          integer
, CONSTRAINT PK_leaderboard PRIMARY KEY
  (
    contestant_number
  )
);

CREATE TABLE votes_count
(
  row_id	     integer    NOT NULL,
  cnt		     integer    NOT NULL
);

-- rollup of votes by phone number, used to reject excessive voting
CREATE VIEW v_votes_by_phone_number
(
  phone_number
, num_votes
)
AS
   SELECT phone_number
        , COUNT(*)
     FROM votes
 GROUP BY phone_number
;

-- rollup of votes by contestant and state for the heat map and results
CREATE VIEW v_votes_by_contestant_number_state
(
  contestant_number
, state
, num_votes
)
AS
   SELECT contestant_number
        , state
        , COUNT(*)
     FROM votes
 GROUP BY contestant_number
        , state
;

CREATE VIEW v_votes_by_contestant
(
  contestant_number
, num_votes
)
AS
   SELECT contestant_number
        , COUNT(*)
     FROM votes
 GROUP BY contestant_number
;


