package com.almagribii.seremind.data.repository

import com.almagribii.seremind.data.local.dao.ArticleDao
import com.almagribii.seremind.data.local.entity.Article

class ArticleRepository(private val articleDao: ArticleDao) {
    suspend fun getAllArticles(): List<Article> = articleDao.getAllArticles()
    suspend fun getArticleById(id: Int): Article? = articleDao.getArticleById(id)
    suspend fun update(article: Article) = articleDao.update(article)
}
