
Using Invoke-WebRequest in powershell 

``` console
$Uri = 'http://localhost:5984/_session'
$Headers = @{
    'content_Type' = 'application/json'
    'content-Length' = '37'
}
$Body = '{"user"="user","password"="password"}'
Invoke-WebRequest -Method Post -Uri $Uri -Headers $Headers -Body $Body
```