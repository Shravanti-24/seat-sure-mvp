-- Releases hold keys, but only the ones still owned by the given user.
--
-- KEYS: the hold keys to release
-- ARGV[1]: the user id that must own them
--
-- Returns the number of keys deleted. Checking the owner stops a late release from one request
-- stealing the hold that another user has since legitimately acquired on the same seat.
local deleted = 0

for i = 1, #KEYS do
    if redis.call('GET', KEYS[i]) == ARGV[1] then
        deleted = deleted + redis.call('DEL', KEYS[i])
    end
end

return deleted
