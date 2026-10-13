-- All-or-nothing seat hold.
--
-- KEYS: one hold key per requested seat, hold:{eventId}:{seatId}
-- ARGV[1]: the user id that will own the holds
-- ARGV[2]: time to live, in seconds
--
-- Returns an empty list when every seat was claimed, otherwise the list of keys that were already
-- held by somebody else. In that case the keys claimed by THIS call are released again, so a failed
-- request never leaves half a basket locked. Running as a single script means no other request can
-- interleave between the individual SET NX calls.
local acquired = {}
local failed = {}

for i = 1, #KEYS do
    if redis.call('SET', KEYS[i], ARGV[1], 'NX', 'EX', ARGV[2]) then
        acquired[#acquired + 1] = KEYS[i]
    else
        failed[#failed + 1] = KEYS[i]
    end
end

if #failed > 0 then
    for i = 1, #acquired do
        redis.call('DEL', acquired[i])
    end
    return failed
end

return {}
