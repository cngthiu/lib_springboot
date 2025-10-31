INSERT INTO dbo.BOOK (TITLE, AUTHOR, PRICE, PUBLISHED_DATE, ISBN) VALUES
(N'Clean Code', N'Robert C. Martin', 39.99, '2008-08-11', N'978-0132350884'),
(N'Effective Java', N'Joshua Bloch', 45.00, '2018-01-06', N'978-0134685991'),
(N'Spring in Action', N'Craig Walls', 49.99, '2018-10-05', N'978-1617294945');


-- Members
INSERT INTO dbo.MEMBER (CODE, NAME, EMAIL) VALUES
('M001', N'Nguyễn Hùng', 'hung@example.com'),
('M002', N'Trần Lan', 'lan@example.com');

-- Example loan (Clean Code to M001, due in 7 days from borrow date)
DECLARE @bookId BIGINT = (SELECT TOP 1 ID FROM dbo.BOOK WHERE TITLE = N'Clean Code');
DECLARE @memberId BIGINT = (SELECT TOP 1 ID FROM dbo.MEMBER WHERE CODE = 'M001');
IF @bookId IS NOT NULL AND @memberId IS NOT NULL
BEGIN
    INSERT INTO dbo.LOAN (BOOK_ID, MEMBER_ID, BORROWED_AT, DUE_AT, STATUS)
    VALUES (@bookId, @memberId, SYSDATETIME(), DATEADD(DAY, 7, SYSDATETIME()), N'BORROWED');
END
